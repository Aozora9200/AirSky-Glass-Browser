"""AirSky Browser の Python 実行ツールと GUI 部品をやり取りする土台。

tkinter / wx / flet の互換モジュールはすべてこの Bridge を通して、画面の部品を
Android の部品(Pixel 風)として作ってもらう。やり取りは 127.0.0.1 のソケットで、1 行 1 つの JSON。

  Python → Android  {"op": "new" | "set" | "layout" | "destroy" | "order" | "dialog" | ...}
  Android → Python  {"ev": "command" | "value" | "key" | "mouse" | "close" | ...}
                    {"reply": <req>, "value": ...}(dialog などの答え)
"""

import base64
import heapq
import itertools
import json
import os
import queue
import re
import socket
import sys
import threading
import time

__all__ = ["bridge", "color", "font", "Event"]


class GuiUnavailable(RuntimeError):
    pass


class Bridge:
    def __init__(self):
        port = int(os.environ.get("AIRSKY_GUI_PORT", "0") or 0)
        if not port:
            raise GuiUnavailable("GUI は AirSky Browser の Python 実行ツールの中でのみ使えます")
        self._sock = socket.create_connection(("127.0.0.1", port))
        self._sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        self._rfile = self._sock.makefile("r", encoding="utf-8", newline="\n")
        self._wlock = threading.Lock()
        self._buf = []
        self._ids = itertools.count(1)
        self._reqs = itertools.count(1)
        self._replies = {}
        self._cond = threading.Condition()
        self.events = queue.Queue()
        self.handlers = {}
        self.dirty = {}
        self._timers = []
        self._timer_seq = itertools.count(1)
        self._cancelled = set()
        self._tlock = threading.Lock()
        self.closed = False
        self.loop_depth = 0
        self.quit_flags = []
        self._raw_send({"op": "hello", "token": os.environ.get("AIRSKY_GUI_TOKEN", ""),
                        "pid": os.getpid()})
        threading.Thread(target=self._reader, name="airsky-gui-reader", daemon=True).start()
        threading.Thread(target=self._flusher, name="airsky-gui-flush", daemon=True).start()

    # ---------------------------------------------------------------- 送る

    def new_id(self):
        return next(self._ids)

    def send(self, msg):
        with self._wlock:
            self._buf.append(msg)

    def mark_dirty(self, key, producer):
        """canvas などの「最後の状態だけ送ればよい」変更。flush の時に producer() を送る。"""
        with self._wlock:
            self.dirty[key] = producer

    def flush(self):
        if self.closed:
            return
        with self._wlock:
            msgs = self._buf
            self._buf = []
            dirty = self.dirty
            self.dirty = {}
        for producer in dirty.values():
            try:
                m = producer()
            except Exception:
                m = None
            if m:
                msgs.append(m)
        if not msgs:
            return
        data = "".join(json.dumps(m, ensure_ascii=False, separators=(",", ":")) + "\n" for m in msgs)
        try:
            self._sock.sendall(data.encode("utf-8"))
        except OSError:
            self._on_closed()

    def _raw_send(self, msg):
        self._sock.sendall((json.dumps(msg, ensure_ascii=False) + "\n").encode("utf-8"))

    def _flusher(self):
        while not self.closed:
            time.sleep(0.03)
            try:
                self.flush()
            except Exception:
                pass

    # ---------------------------------------------------------------- 受ける

    def _reader(self):
        try:
            for line in self._rfile:
                line = line.strip()
                if not line:
                    continue
                try:
                    m = json.loads(line)
                except ValueError:
                    continue
                if "reply" in m:
                    with self._cond:
                        self._replies[m["reply"]] = m.get("value")
                        self._cond.notify_all()
                else:
                    self.events.put(m)
        except OSError:
            pass
        self._on_closed()

    def _on_closed(self):
        if self.closed:
            return
        self.closed = True
        with self._cond:
            self._cond.notify_all()
        self.events.put({"ev": "quit"})

    def request(self, msg, timeout=None):
        """答えが必要な依頼(ダイアログなど)。答えが来るまで待つ。"""
        rid = next(self._reqs)
        msg = dict(msg)
        msg["req"] = rid
        self.send(msg)
        self.flush()
        with self._cond:
            while rid not in self._replies and not self.closed:
                self._cond.wait(0.25 if timeout is None else timeout)
                if timeout is not None:
                    break
            return self._replies.pop(rid, None)

    # ---------------------------------------------------------------- タイマー

    def call_later(self, ms, fn, *args):
        tid = next(self._timer_seq)
        when = time.monotonic() + max(0, ms) / 1000.0
        with self._tlock:
            heapq.heappush(self._timers, (when, tid, fn, args))
        return tid

    def cancel(self, tid):
        with self._tlock:
            self._cancelled.add(tid)

    def _next_timer_delay(self):
        with self._tlock:
            while self._timers and self._timers[0][1] in self._cancelled:
                _, tid, _, _ = heapq.heappop(self._timers)
                self._cancelled.discard(tid)
            if not self._timers:
                return None
            return max(0.0, self._timers[0][0] - time.monotonic())

    def _run_due_timers(self):
        ran = False
        now = time.monotonic()
        while True:
            with self._tlock:
                if not self._timers or self._timers[0][0] > now:
                    break
                _, tid, fn, args = heapq.heappop(self._timers)
                if tid in self._cancelled:
                    self._cancelled.discard(tid)
                    continue
            ran = True
            self._call(fn, *args)
        return ran

    # ---------------------------------------------------------------- ループ

    def _call(self, fn, *args):
        try:
            return fn(*args)
        except SystemExit:
            raise
        except KeyboardInterrupt:
            raise
        except Exception:
            import traceback
            sys.stderr.write("Exception in GUI callback\n")
            traceback.print_exc()

    def dispatch(self, ev):
        if ev.get("ev") == "quit":
            for flag in self.quit_flags:
                flag[0] = True
            return False
        target = ev.get("id")
        h = self.handlers.get(target)
        if h is not None:
            self._call(h, ev)
        return True

    def process_pending(self):
        """たまっている出来事とタイマーを、待たずに全部片付ける(tkinter の update())。"""
        self._run_due_timers()
        while True:
            try:
                ev = self.events.get_nowait()
            except queue.Empty:
                break
            if not self.dispatch(ev):
                break
        self.flush()

    def mainloop(self, stop=None):
        """stop()[0] が True になるか、Android 側が閉じるまで出来事を処理する。"""
        flag = [False]
        self.quit_flags.append(flag)
        self.loop_depth += 1
        try:
            while not flag[0] and not self.closed and not (stop and stop()):
                self._run_due_timers()
                self.flush()
                delay = self._next_timer_delay()
                try:
                    ev = self.events.get(timeout=0.2 if delay is None else min(delay, 0.2))
                except queue.Empty:
                    continue
                self.dispatch(ev)
                # 続けて来ている出来事はまとめて処理する
                for _ in range(64):
                    try:
                        ev = self.events.get_nowait()
                    except queue.Empty:
                        break
                    self.dispatch(ev)
            self.flush()
        finally:
            self.loop_depth -= 1
            self.quit_flags.remove(flag)
        return flag

    def quit_all(self):
        for flag in self.quit_flags:
            flag[0] = True

    # ---------------------------------------------------------------- 便利

    def dialog(self, kind, **kw):
        kw["op"] = "dialog"
        kw["kind"] = kind
        return self.request(kw)

    def toast(self, text):
        self.send({"op": "toast", "text": str(text)})


_bridge = None
_bridge_lock = threading.Lock()


def bridge():
    global _bridge
    with _bridge_lock:
        if _bridge is None:
            _bridge = Bridge()
        return _bridge


# ===================================================================== 色

_NAMED = {
    "aliceblue": "f0f8ff", "antiquewhite": "faebd7", "aqua": "00ffff", "aquamarine": "7fffd4",
    "azure": "f0ffff", "beige": "f5f5dc", "bisque": "ffe4c4", "black": "000000",
    "blanchedalmond": "ffebcd", "blue": "0000ff", "blueviolet": "8a2be2", "brown": "a52a2a",
    "burlywood": "deb887", "cadetblue": "5f9ea0", "chartreuse": "7fff00", "chocolate": "d2691e",
    "coral": "ff7f50", "cornflowerblue": "6495ed", "cornsilk": "fff8dc", "crimson": "dc143c",
    "cyan": "00ffff", "darkblue": "00008b", "darkcyan": "008b8b", "darkgoldenrod": "b8860b",
    "darkgray": "a9a9a9", "darkgrey": "a9a9a9", "darkgreen": "006400", "darkkhaki": "bdb76b",
    "darkmagenta": "8b008b", "darkolivegreen": "556b2f", "darkorange": "ff8c00",
    "darkorchid": "9932cc", "darkred": "8b0000", "darksalmon": "e9967a", "darkseagreen": "8fbc8f",
    "darkslateblue": "483d8b", "darkslategray": "2f4f4f", "darkslategrey": "2f4f4f",
    "darkturquoise": "00ced1", "darkviolet": "9400d3", "deeppink": "ff1493",
    "deepskyblue": "00bfff", "dimgray": "696969", "dimgrey": "696969", "dodgerblue": "1e90ff",
    "firebrick": "b22222", "floralwhite": "fffaf0", "forestgreen": "228b22", "fuchsia": "ff00ff",
    "gainsboro": "dcdcdc", "ghostwhite": "f8f8ff", "gold": "ffd700", "goldenrod": "daa520",
    "gray": "bebebe", "grey": "bebebe", "green": "00ff00", "greenyellow": "adff2f",
    "honeydew": "f0fff0", "hotpink": "ff69b4", "indianred": "cd5c5c", "indigo": "4b0082",
    "ivory": "fffff0", "khaki": "f0e68c", "lavender": "e6e6fa", "lavenderblush": "fff0f5",
    "lawngreen": "7cfc00", "lemonchiffon": "fffacd", "lightblue": "add8e6", "lightcoral": "f08080",
    "lightcyan": "e0ffff", "lightgoldenrodyellow": "fafad2", "lightgray": "d3d3d3",
    "lightgrey": "d3d3d3", "lightgreen": "90ee90", "lightpink": "ffb6c1", "lightsalmon": "ffa07a",
    "lightseagreen": "20b2aa", "lightskyblue": "87cefa", "lightslategray": "778899",
    "lightslategrey": "778899", "lightsteelblue": "b0c4de", "lightyellow": "ffffe0",
    "lime": "00ff00", "limegreen": "32cd32", "linen": "faf0e6", "magenta": "ff00ff",
    "maroon": "b03060", "mediumaquamarine": "66cdaa", "mediumblue": "0000cd",
    "mediumorchid": "ba55d3", "mediumpurple": "9370db", "mediumseagreen": "3cb371",
    "mediumslateblue": "7b68ee", "mediumspringgreen": "00fa9a", "mediumturquoise": "48d1cc",
    "mediumvioletred": "c71585", "midnightblue": "191970", "mintcream": "f5fffa",
    "mistyrose": "ffe4e1", "moccasin": "ffe4b5", "navajowhite": "ffdead", "navy": "000080",
    "navyblue": "000080", "oldlace": "fdf5e6", "olive": "808000", "olivedrab": "6b8e23",
    "orange": "ffa500", "orangered": "ff4500", "orchid": "da70d6", "palegoldenrod": "eee8aa",
    "palegreen": "98fb98", "paleturquoise": "afeeee", "palevioletred": "db7093",
    "papayawhip": "ffefd5", "peachpuff": "ffdab9", "peru": "cd853f", "pink": "ffc0cb",
    "plum": "dda0dd", "powderblue": "b0e0e6", "purple": "a020f0", "rebeccapurple": "663399",
    "red": "ff0000", "rosybrown": "bc8f8f", "royalblue": "4169e1", "saddlebrown": "8b4513",
    "salmon": "fa8072", "sandybrown": "f4a460", "seagreen": "2e8b57", "seashell": "fff5ee",
    "sienna": "a0522d", "silver": "c0c0c0", "skyblue": "87ceeb", "slateblue": "6a5acd",
    "slategray": "708090", "slategrey": "708090", "snow": "fffafa", "springgreen": "00ff7f",
    "steelblue": "4682b4", "tan": "d2b48c", "teal": "008080", "thistle": "d8bfd8",
    "tomato": "ff6347", "turquoise": "40e0d0", "violet": "ee82ee", "wheat": "f5deb3",
    "white": "ffffff", "whitesmoke": "f5f5f5", "yellow": "ffff00", "yellowgreen": "9acd32",
    # Tk / Windows のシステム色(Pixel 風の見た目に寄せる)
    "systembuttonface": "f0f0f0", "systemwindow": "ffffff", "systembuttontext": "000000",
    "systemwindowtext": "000000", "systemhighlight": "0b57d0", "systemhighlighttext": "ffffff",
    "systemdisabledtext": "8d8d8d", "systembutton": "f0f0f0", "systemwindowbody": "ffffff",
}

# Material Design の色(flet の ft.Colors.BLUE_200 など)。500 を基準に白・黒と混ぜて段階を作る
_MATERIAL = {
    "red": "f44336", "pink": "e91e63", "purple": "9c27b0", "deeppurple": "673ab7",
    "indigo": "3f51b5", "blue": "2196f3", "lightblue": "03a9f4", "cyan": "00bcd4",
    "teal": "009688", "green": "4caf50", "lightgreen": "8bc34a", "lime": "cddc39",
    "yellow": "ffeb3b", "amber": "ffc107", "orange": "ff9800", "deeporange": "ff5722",
    "brown": "795548", "grey": "9e9e9e", "bluegrey": "607d8b",
}
_SHADES = {50: ("w", .90), 100: ("w", .78), 200: ("w", .60), 300: ("w", .40), 400: ("w", .20),
           500: ("w", 0), 600: ("b", .10), 700: ("b", .20), 800: ("b", .30), 900: ("b", .45)}

# flet のテーマの色名
_THEME = {
    "primary": "0b57d0", "onprimary": "ffffff", "primarycontainer": "d3e3fd",
    "onprimarycontainer": "041e49", "secondary": "00639b", "onsecondary": "ffffff",
    "secondarycontainer": "c2e7ff", "onsecondarycontainer": "001d35", "tertiary": "146c2e",
    "tertiarycontainer": "c4eed0", "ontertiarycontainer": "072711", "error": "b3261e",
    "onerror": "ffffff", "errorcontainer": "f9dedc", "background": "ffffff", "onbackground": "1f1f1f",
    "surface": "ffffff", "onsurface": "1f1f1f", "surfacevariant": "e1e3e1",
    "onsurfacevariant": "444746", "outline": "747775", "outlinevariant": "c4c7c5",
    "surfacecontainer": "f0f4f9", "surfacecontainerhigh": "e9eef6", "surfacecontainerhighest": "dde3ea",
    "surfacecontainerlow": "f8fafd", "inversesurface": "303030", "shadow": "000000",
    "transparent": "00000000",
}


def _mix(hexrgb, kind, t):
    r, g, b = int(hexrgb[0:2], 16), int(hexrgb[2:4], 16), int(hexrgb[4:6], 16)
    target = 255 if kind == "w" else 0
    r, g, b = (round(c + (target - c) * t) for c in (r, g, b))
    return "%02x%02x%02x" % (r, g, b)


def color(value, default=None):
    """色の指定(tk の名前・#rgb・#rrggbb・(r,g,b)・wx.Colour・flet の名前)を "#aarrggbb" / "#rrggbb" にする。"""
    if value is None or value == "":
        return default
    if hasattr(value, "_airsky_color"):
        return value._airsky_color()
    if isinstance(value, (tuple, list)):
        vals = [int(v) for v in value[:4]]
        if len(vals) == 4:
            return "#%02x%02x%02x%02x" % (vals[3], vals[0], vals[1], vals[2])
        return "#%02x%02x%02x" % tuple(vals[:3])
    s = str(value).strip()
    if s.startswith("#"):
        h = s[1:]
        if len(h) == 3:
            return "#" + "".join(c * 2 for c in h)
        if len(h) == 12:  # #rrrrggggbbbb
            return "#" + h[0:2] + h[4:6] + h[8:10]
        if len(h) in (6, 8):
            return "#" + h.lower()
        return default
    key = s.lower().replace(" ", "").replace("_", "")
    # flet: "blue200"、"blue,0.5"(不透明度付き)
    opacity = None
    if "," in key:
        key, _, op = key.partition(",")
        try:
            opacity = float(op)
        except ValueError:
            opacity = None
    rgb = None
    m = re.match(r"^([a-z]+?)(accent)?(\d{2,3})?$", key)
    if key in _THEME:
        rgb = _THEME[key]
    elif m and m.group(1) in _MATERIAL and (m.group(3) or m.group(2)):
        base = _MATERIAL[m.group(1)]
        shade = int(m.group(3) or 200)
        kind, t = _SHADES.get(shade, ("w", 0))
        rgb = _mix(base, kind, t)
    elif key in _NAMED:
        rgb = _NAMED[key]
    elif m and m.group(1) in _MATERIAL:
        rgb = _MATERIAL[m.group(1)]
    elif re.match(r"^(gray|grey)\d{1,3}$", key):
        n = int(re.sub(r"\D", "", key))
        v = round(255 * min(100, n) / 100)
        rgb = "%02x%02x%02x" % (v, v, v)
    else:
        m2 = re.match(r"^([a-z]+)([1-4])$", key)  # tk の "red3" など
        if m2 and m2.group(1) in _NAMED:
            rgb = _mix(_NAMED[m2.group(1)], "b", (int(m2.group(2)) - 1) * 0.12)
    if rgb is None:
        return default
    if len(rgb) == 8:
        return "#" + rgb
    if opacity is not None:
        return "#%02x%s" % (max(0, min(255, round(opacity * 255))), rgb)
    return "#" + rgb


def rgb_tuple(value):
    c = color(value)
    if not c:
        return (0, 0, 0)
    h = c[-6:]
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16))


# ===================================================================== 文字の形

def font(spec, default_size=None):
    """tk / wx / flet の文字の指定を {"family", "size"(sp), "bold", "italic", "underline"} にする。"""
    if spec is None or spec == "":
        return None
    if hasattr(spec, "_airsky_font"):
        return spec._airsky_font()
    family, size, styles = None, None, []
    if isinstance(spec, (tuple, list)):
        parts = list(spec)
        if parts:
            family = parts[0]
        if len(parts) > 1:
            size = parts[1]
        for p in parts[2:]:
            styles += str(p).split()
    else:
        s = str(spec)
        m = re.match(r"^\{([^}]*)\}\s*(.*)$", s)
        if m:
            family, rest = m.group(1), m.group(2).split()
        else:
            toks = s.split()
            family, rest = (toks[0] if toks else None), toks[1:]
        if rest:
            size = rest[0]
            styles = rest[1:]
    try:
        size = int(float(size)) if size is not None else None
    except (TypeError, ValueError):
        styles.append(str(size))
        size = None
    out = {"family": _family(family)}
    if size:
        # tk の大きさはポイント(1pt = 1.333px)、負の数はピクセル
        out["size"] = round(size * 1.333, 1) if size > 0 else -size
    elif default_size:
        out["size"] = default_size
    st = [x.lower() for x in styles]
    if "bold" in st:
        out["bold"] = True
    if "italic" in st:
        out["italic"] = True
    if "underline" in st:
        out["underline"] = True
    if "overstrike" in st:
        out["strike"] = True
    return out


def _family(f):
    if not f:
        return "sans"
    f = str(f).lower()
    if any(k in f for k in ("courier", "mono", "consol", "menlo", "terminal", "fixed", "code", "ｍｓ ゴシック", "ms gothic")):
        return "mono"
    if any(k in f for k in ("times", "serif", "georgia", "mincho", "明朝", "garamond", "palatino")) and "sans" not in f:
        return "serif"
    return "sans"


# ===================================================================== 画像

def image_ref(path=None, data=None, fmt=None):
    """画像を Android に渡す形(ファイルのフルパスか、base64 のデータ)にする。"""
    if data is not None:
        if isinstance(data, str):
            try:
                base64.b64decode(data, validate=True)
                return {"data": data}
            except Exception:
                return {"data": base64.b64encode(data.encode("latin-1")).decode("ascii")}
        return {"data": base64.b64encode(bytes(data)).decode("ascii")}
    if path:
        p = str(path)
        if p.startswith(("http://", "https://")):
            return {"url": p}
        return {"path": os.path.abspath(p)}
    return None


class Event:
    """bind したコールバックに渡す出来事(tkinter.Event と同じ名前の属性を持つ)。"""

    def __init__(self, **kw):
        self.widget = None
        self.x = self.y = self.x_root = self.y_root = 0
        self.char = ""
        self.keysym = "??"
        self.keycode = 0
        self.keysym_num = 0
        self.num = "??"
        self.width = self.height = 0
        self.delta = 0
        self.state = 0
        self.type = "??"
        self.serial = 0
        self.time = int(time.time() * 1000) & 0x7FFFFFFF
        self.__dict__.update(kw)

    def __repr__(self):
        keys = ("type", "widget", "x", "y", "keysym", "char", "num", "width", "height")
        return "<%s event %s>" % (self.type, " ".join("%s=%r" % (k, getattr(self, k)) for k in keys))
