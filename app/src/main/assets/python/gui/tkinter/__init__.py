"""AirSky Browser の tkinter 互換モジュール。

Android には Tcl/Tk が無いので、tkinter と同じ使い方のまま、画面の部品を Android の部品(Pixel 風)で
作って表示する。座標・大きさの 1 ピクセルは Android の 1dp として扱う。

対応しているもの(主なもの):
  Tk / Toplevel / Frame / LabelFrame / Label / Message / Button / Entry / Text / Checkbutton /
  Radiobutton / Scale / Spinbox / Listbox / Scrollbar / Canvas / Menu / Menubutton / OptionMenu /
  PanedWindow / PhotoImage、ttk(tkinter.ttk)、messagebox・simpledialog・filedialog・colorchooser・
  font・scrolledtext、pack / grid / place、bind(マウス・キー・仮想イベント)、after、各種 Variable
"""

import itertools
import os
import re
import struct
import sys
import time

import _airsky_gui as _g
from tkinter.constants import *  # noqa: F401,F403

TkVersion = 8.6
TclVersion = 8.6
READABLE = 2
WRITABLE = 4
EXCEPTION = 8
wantobjects = 1

_default_root = None
_support_default_root = True
_counter = itertools.count(1)
_all_bindings = {}
_widgets_by_path = {}


class TclError(Exception):
    pass


def NoDefaultRoot():
    global _support_default_root, _default_root
    _support_default_root = False
    _default_root = None


def _get_default_root(what=None):
    global _default_root
    if _default_root is None:
        if not _support_default_root:
            raise RuntimeError("No master specified and tkinter is configured to not support default root")
        Tk()
    return _default_root


def _bridge():
    return _g.bridge()


# ===================================================================== 値の変換

_UNITS = {"c": 37.8, "i": 96.0, "m": 3.78, "p": 1.333}


def _px(v, default=0):
    """"2c" "1i" "10" 10 などをピクセル(= dp)にする。"""
    if v is None or v == "":
        return default
    if isinstance(v, (int, float)):
        return float(v)
    s = str(v).strip()
    try:
        if s and s[-1] in _UNITS:
            return float(s[:-1]) * _UNITS[s[-1]]
        return float(s)
    except ValueError:
        return default


def _pair(v):
    """padx=5 / padx=(5, 10) / padx="5 10" → [5, 10]"""
    if v is None or v == "":
        return [0, 0]
    if isinstance(v, (tuple, list)):
        if len(v) == 1:
            return [_px(v[0]), _px(v[0])]
        return [_px(v[0]), _px(v[1])]
    parts = str(v).split()
    if len(parts) >= 2:
        return [_px(parts[0]), _px(parts[1])]
    return [_px(v), _px(v)]


def _bool(v):
    if isinstance(v, str):
        return v.strip().lower() in ("1", "true", "yes", "on")
    return bool(v)


def _flatten(seq):
    out = []
    for x in seq:
        if isinstance(x, (tuple, list)):
            out.extend(_flatten(x))
        else:
            out.append(x)
    return out


def _cnfmerge(cnfs):
    if isinstance(cnfs, dict):
        return dict(cnfs)
    out = {}
    for c in cnfs or ():
        if c:
            out.update(c)
    return out


def _norm_opt(k):
    k = k.rstrip("_") if k.endswith("_") and k not in ("in_",) else k
    return {"bd": "borderwidth", "bg": "background", "fg": "foreground"}.get(k, k)


# ===================================================================== 変数

class Variable:
    _default = ""
    _tk = None

    def __init__(self, master=None, value=None, name=None):
        self._name = name or "PY_VAR%d" % next(_counter)
        self._value = self._default if value is None else value
        self._traces = []  # [modes, cbname, callback]
        self._links = []

    def __str__(self):
        return self._name

    def __repr__(self):
        return "<%s %s=%r>" % (type(self).__name__, self._name, self._value)

    def __del__(self):
        pass

    def set(self, value):
        self._value = value
        for w in list(self._links):
            try:
                w._var_changed(self)
            except Exception:
                pass
        self._fire("write")

    initialize = set

    def get(self):
        self._fire("read")
        return self._value

    def _fire(self, mode):
        for modes, cbname, cb in list(self._traces):
            if mode in modes:
                try:
                    if cb.__dict__.get("_old_style"):
                        cb(self._name, "", mode[0])
                    else:
                        cb(self._name, "", mode)
                except Exception:
                    import traceback
                    traceback.print_exc()

    def trace_add(self, mode, callback):
        modes = tuple(mode) if isinstance(mode, (tuple, list)) else (mode,)
        modes = tuple({"w": "write", "r": "read", "u": "unset"}.get(m, m) for m in modes)
        cbname = "%d%s" % (id(callback), getattr(callback, "__name__", "cb"))
        self._traces.append([modes, cbname, callback])
        return cbname

    def trace_remove(self, mode, cbname):
        self._traces = [t for t in self._traces if t[1] != cbname]

    def trace_info(self):
        return [(t[0], t[1]) for t in self._traces]

    def trace_variable(self, mode, callback):
        def wrapper(name, index, m, _cb=callback):
            return _cb(name, index, m)
        wrapper._old_style = True
        modes = tuple({"w": "write", "r": "read", "u": "unset"}.get(m, m) for m in mode)
        cbname = "%d%s" % (id(callback), getattr(callback, "__name__", "cb"))
        self._traces.append([modes, cbname, wrapper])
        return cbname

    trace = trace_variable

    def trace_vdelete(self, mode, cbname):
        self.trace_remove(mode, cbname)

    def trace_vinfo(self):
        return self.trace_info()

    def __eq__(self, other):
        return isinstance(other, Variable) and other._name == self._name

    def __hash__(self):
        return hash(self._name)

    def _link(self, w):
        if w not in self._links:
            self._links.append(w)


class StringVar(Variable):
    _default = ""

    def get(self):
        v = Variable.get(self)
        return v if isinstance(v, str) else str(v)


class IntVar(Variable):
    _default = 0

    def get(self):
        v = Variable.get(self)
        try:
            return int(v)
        except (TypeError, ValueError):
            try:
                return int(float(v))
            except (TypeError, ValueError):
                raise TclError('expected integer but got "%s"' % v)


class DoubleVar(Variable):
    _default = 0.0

    def get(self):
        v = Variable.get(self)
        try:
            return float(v)
        except (TypeError, ValueError):
            raise TclError('expected floating-point number but got "%s"' % v)


class BooleanVar(Variable):
    _default = False

    def set(self, value):
        Variable.set(self, _bool(value))

    initialize = set

    def get(self):
        return _bool(Variable.get(self))


def getint(s):
    return int(s)


def getdouble(s):
    return float(s)


def getboolean(s):
    return _bool(s)


def mainloop(n=0):
    _get_default_root().mainloop(n)


def image_names():
    return tuple(_images.keys())


def image_types():
    return ("photo", "bitmap")


# ===================================================================== bind の解釈

_MODS = {"control": "Control", "shift": "Shift", "alt": "Alt", "meta": "Alt", "option": "Alt",
         "command": "Control", "mod1": "Alt", "lock": "Lock", "double": "Double", "triple": "Triple",
         "b1": "B1", "b2": "B2", "b3": "B3", "button1": "B1", "button2": "B2", "button3": "B3", "any": "Any"}
_TYPES = {"button": "ButtonPress", "buttonpress": "ButtonPress", "buttonrelease": "ButtonRelease",
          "motion": "Motion", "key": "KeyPress", "keypress": "KeyPress", "keyrelease": "KeyRelease",
          "enter": "Enter", "leave": "Leave", "configure": "Configure", "focusin": "FocusIn",
          "focusout": "FocusOut", "mousewheel": "MouseWheel", "destroy": "Destroy", "map": "Map",
          "unmap": "Unmap", "expose": "Expose", "visibility": "Visibility", "activate": "Activate",
          "deactivate": "Deactivate"}


def _parse_seq(seq):
    """"<Control-Button-1>" → ("ButtonPress", "1", frozenset({"Control"}))"""
    seq = seq.strip()
    if seq.startswith("<<") and seq.endswith(">>"):
        return ("Virtual", seq[2:-2], frozenset())
    if not (seq.startswith("<") and seq.endswith(">")):
        # "a" のような 1 文字は KeyPress-a
        return ("KeyPress", seq[:1] if seq else None, frozenset())
    parts = seq[1:-1].split("-")
    mods, etype, detail = set(), None, None
    for i, p in enumerate(parts):
        low = p.lower()
        if etype is None and low in _MODS and i < len(parts) - 1:
            mods.add(_MODS[low])
        elif etype is None and low in _TYPES:
            etype = _TYPES[low]
        else:
            detail = p
    if etype is None:
        if detail is not None and detail.isdigit():
            etype = "ButtonPress"
        else:
            etype = "KeyPress"
    if etype in ("ButtonPress", "ButtonRelease") and detail is not None and not detail.isdigit():
        detail = None
    if etype == "KeyPress" and detail is not None and len(detail) == 1 and detail.isupper():
        mods.add("Shift")
    mods.discard("Any")
    return (etype, detail, frozenset(mods))


def _add_binding(table, sequence, func, add):
    key = _parse_seq(sequence)
    funcid = "%d%s" % (id(func), getattr(func, "__name__", "f"))
    if add and str(add).startswith("+") or add is True:
        table.setdefault(key, []).append((funcid, func))
    else:
        table[key] = [(funcid, func)]
    return funcid


def _match(table, etype, detail, mods):
    best, best_score = None, -1
    for (t, d, m), funcs in table.items():
        if t != etype:
            continue
        if d is not None and (detail is None or str(d) != str(detail)):
            continue
        if not m <= mods:
            continue
        score = (4 if d is not None else 0) + len(m)
        if score > best_score:
            best, best_score = funcs, score
    return best or []


def _dispatch(widget, etype, detail, ev, mods=frozenset()):
    """bindtags(部品 → その窓 → all)の順にコールバックを呼ぶ。"break" で止まる。"""
    chain = []
    if widget is not None:
        chain.append(widget._bindings)
        cls = _class_bindings.get(widget.winfo_class())
        if cls:
            chain.append(cls)
        top = widget._toplevel()
        if top is not None and top is not widget:
            chain.append(top._bindings)
    chain.append(_all_bindings)
    handled = False
    for table in chain:
        for _, fn in list(_match(table, etype, detail, mods)):
            handled = True
            r = _g.bridge()._call(fn, ev) if widget is not None else fn(ev)
            if r == "break":
                return "break"
    return handled


_class_bindings = {}


_KEYSYM_CHARS = {"Return": "\r", "space": " ", "Tab": "\t", "BackSpace": "\b", "Escape": "\x1b",
                 "Delete": "\x7f"}


# ===================================================================== 画像

_images = {}


class Image:
    _type = "photo"

    def __init__(self, imgtype="photo", name=None, cnf={}, master=None, **kw):
        kw = _cnfmerge((cnf, kw))
        self.name = name or "pyimage%d" % next(_counter)
        self._file = kw.get("file")
        self._data = kw.get("data")
        self._scale = 1.0
        self._w, self._h = int(_px(kw.get("width"), 0)), int(_px(kw.get("height"), 0))
        _images[self.name] = self
        self._read_size()

    def __str__(self):
        return self.name

    def _read_size(self):
        head = b""
        try:
            if self._file:
                with open(self._file, "rb") as f:
                    head = f.read(32)
            elif self._data:
                import base64
                raw = self._data if isinstance(self._data, bytes) else self._data.encode("latin-1")
                try:
                    raw = base64.b64decode(raw, validate=True)
                except Exception:
                    pass
                head = raw[:32]
        except OSError as e:
            raise TclError('couldn\'t open "%s": %s' % (self._file, e.strerror))
        if head[:8] == b"\x89PNG\r\n\x1a\n":
            self._w, self._h = struct.unpack(">II", head[16:24])
        elif head[:6] in (b"GIF87a", b"GIF89a"):
            self._w, self._h = struct.unpack("<HH", head[6:10])

    def _ref(self):
        ref = _g.image_ref(self._file, self._data)
        if ref is None:
            return None
        if self._scale != 1.0:
            ref["scale"] = self._scale
        return ref

    def width(self):
        return int(self._w * self._scale)

    def height(self):
        return int(self._h * self._scale)

    def type(self):
        return self._type

    def configure(self, **kw):
        if "file" in kw:
            self._file, self._data = kw["file"], None
        if "data" in kw:
            self._data, self._file = kw["data"], None
        self._read_size()

    config = configure

    def cget(self, key):
        return {"file": self._file, "data": self._data, "width": self.width(),
                "height": self.height()}.get(key, "")

    def __getitem__(self, key):
        return self.cget(key)

    def _copy(self, scale):
        img = PhotoImage(file=self._file, data=self._data)
        img._scale = self._scale * scale
        return img

    def copy(self):
        return self._copy(1.0)

    def zoom(self, x, y=""):
        return self._copy(float(x))

    def subsample(self, x, y=""):
        return self._copy(1.0 / float(x or 1))

    def blank(self):
        pass

    def put(self, data, to=None):
        pass

    def get(self, x, y):
        return (0, 0, 0)

    def write(self, filename, format=None, from_coords=None):
        if self._file:
            import shutil
            shutil.copyfile(self._file, filename)


class PhotoImage(Image):
    def __init__(self, name=None, cnf={}, master=None, **kw):
        Image.__init__(self, "photo", name, cnf, master, **kw)


class BitmapImage(Image):
    _type = "bitmap"

    def __init__(self, name=None, cnf={}, master=None, **kw):
        self.name = name or "pyimage%d" % next(_counter)
        self._file = self._data = None
        self._scale = 1.0
        self._w = self._h = 16


# ===================================================================== 共通の土台

class _FakeTk:
    """root.tk.call(...) などの Tcl 呼び出しを受け流す。"""

    def call(self, *args):
        args = _flatten(args)
        if args[:2] == ["tk", "scaling"]:
            return 1.0
        if args[:1] == ["info"] and args[1:2] == ["patchlevel"]:
            return "8.6.13"
        return ""

    def eval(self, script):
        return ""

    def splitlist(self, s):
        if isinstance(s, (tuple, list)):
            return tuple(s)
        return tuple(str(s).split())

    split = splitlist

    def getboolean(self, s):
        return _bool(s)

    def getint(self, s):
        return int(s)

    def getdouble(self, s):
        return float(s)

    def createcommand(self, name, func):
        pass

    def deletecommand(self, name):
        pass

    def globalsetvar(self, *a):
        pass

    def globalgetvar(self, *a):
        return ""

    def willdispatch(self):
        pass

    def wantobjects(self):
        return 1

    def mainloop(self, n=0):
        _get_default_root().mainloop()

    def quit(self):
        _g.bridge().quit_all()


_faketk = _FakeTk()


class Misc:
    """すべての部品と Tk の共通部分。"""

    _type = "frame"
    _container = False
    _wclass = "Frame"
    _defaults = {}
    _last_child_ids = None

    # ---- 作る

    def _setup(self, master, cnf, kw, name=None):
        kw = _cnfmerge((cnf, kw))
        self.tk = _faketk
        self.master = master
        self.children = {}
        self._bindings = {}
        self._destroyed = False
        self._id = _g.bridge().new_id()
        self._opts = {}
        for k, v in self._defaults.items():
            self._opts[k] = v
        name = kw.pop("name", name)
        for k, v in kw.items():
            self._opts[_norm_opt(k)] = v
        cls = type(self).__name__.lower()
        if master is None:
            self._name = ""
            self._w = "."
        else:
            if not name:
                cnt = getattr(master, "_child_counts", None)
                if cnt is None:
                    cnt = master._child_counts = {}
                cnt[cls] = cnt.get(cls, 0) + 1
                name = "!%s%s" % (cls, cnt[cls] if cnt[cls] > 1 else "")
            self._name = name
            self._w = (master._w if master._w != "." else "") + "." + name
            master.children[name] = self
        _widgets_by_path[self._w] = self
        self._pack = None
        self._grid = None
        self._place = None
        self._pack_list = []
        self._rows = {}
        self._cols = {}
        self._propagate = True
        self._size = (0, 0)
        self._mouse_flag = False
        self._var_links()
        _g.bridge().handlers[self._id] = self._on_event

    def _create(self, parent_id):
        msg = {"op": "new", "id": self._id, "type": self._type, "props": self._props()}
        if parent_id is not None:
            msg["parent"] = parent_id
        _g.bridge().send(msg)
        self._update_mouse_flag()

    def __str__(self):
        return self._w

    def __repr__(self):
        return "<%s.%s object %s>" % (self.__class__.__module__, self.__class__.__name__, self._w)

    # ---- オプション

    def _var_links(self):
        for key in ("textvariable", "variable", "listvariable"):
            v = self._opts.get(key)
            if isinstance(v, Variable):
                v._link(self)

    def _var_changed(self, var):
        self._send_props()

    def _props(self):
        return {}

    def _send_props(self):
        if self._destroyed:
            return
        _g.bridge().send({"op": "set", "id": self._id, "props": self._props()})

    def configure(self, cnf=None, **kw):
        kw = _cnfmerge((cnf, kw))
        if not kw:
            return {k: (k, k, k.capitalize(), "", v) for k, v in self._opts.items()}
        for k, v in kw.items():
            self._set_opt(_norm_opt(k), v)
        self._var_links()
        self._send_props()
        self._update_mouse_flag()

    config = configure

    def _set_opt(self, key, value):
        self._opts[key] = value

    def cget(self, key):
        key = _norm_opt(key)
        v = self._opts.get(key, self._defaults.get(key, ""))
        return v

    def __setitem__(self, key, value):
        self.configure({key: value})

    def __getitem__(self, key):
        return self.cget(key)

    def keys(self):
        return list(self._opts.keys())

    # ---- 文字・色の共通オプション

    def _text_value(self):
        tv = self._opts.get("textvariable")
        if isinstance(tv, Variable):
            return str(tv.get())
        t = self._opts.get("text", "")
        return "" if t is None else str(t)

    def _style_props(self, p):
        fg = _g.color(self._opts.get("foreground"))
        if fg:
            p["fg"] = fg
        bg = _g.color(self._opts.get("background"))
        if bg:
            p["bg"] = bg
        f = _g.font(self._opts.get("font"))
        if f:
            p["font"] = f
        st = self._opts.get("state")
        if st:
            p["state"] = str(st)
        bw = _px(self._opts.get("borderwidth"), 0)
        relief = str(self._opts.get("relief", "") or "")
        hl = _px(self._opts.get("highlightthickness"), 0)
        if (bw > 0 and relief not in ("", "flat")) or hl > 0:
            p["border"] = {"width": max(1.0, min(bw or hl, 3)),
                           "color": _g.color(self._opts.get("highlightbackground")) or "#c4c7c5",
                           "relief": relief or "solid"}
        return p

    # ---- 窓の情報

    def _toplevel(self):
        w = self
        while w is not None and not isinstance(w, Wm):
            w = w.master
        return w

    winfo_toplevel = _toplevel

    def winfo_exists(self):
        return 0 if self._destroyed else 1

    def winfo_children(self):
        return [c for c in self.children.values() if not c._destroyed]

    def winfo_width(self):
        return int(self._size[0]) or 1

    def winfo_height(self):
        return int(self._size[1]) or 1

    def winfo_reqwidth(self):
        return int(_px(self._opts.get("width"), 0)) or self.winfo_width()

    def winfo_reqheight(self):
        return int(_px(self._opts.get("height"), 0)) or self.winfo_height()

    def winfo_x(self):
        return int(getattr(self, "_pos", (0, 0))[0])

    def winfo_y(self):
        return int(getattr(self, "_pos", (0, 0))[1])

    def winfo_rootx(self):
        return self.winfo_x()

    def winfo_rooty(self):
        return self.winfo_y()

    def winfo_screenwidth(self):
        return int(os.environ.get("AIRSKY_SCREEN_W", "400"))

    def winfo_screenheight(self):
        return int(os.environ.get("AIRSKY_SCREEN_H", "800"))

    def winfo_screenmmwidth(self):
        return int(self.winfo_screenwidth() / 3.78)

    def winfo_screenmmheight(self):
        return int(self.winfo_screenheight() / 3.78)

    def winfo_vrootwidth(self):
        return self.winfo_screenwidth()

    def winfo_vrootheight(self):
        return self.winfo_screenheight()

    def winfo_pointerxy(self):
        return (0, 0)

    def winfo_pointerx(self):
        return 0

    def winfo_pointery(self):
        return 0

    def winfo_ismapped(self):
        return 0 if self._destroyed else 1

    def winfo_viewable(self):
        return self.winfo_ismapped()

    def winfo_manager(self):
        if self._pack is not None:
            return "pack"
        if self._grid is not None:
            return "grid"
        if self._place is not None:
            return "place"
        return ""

    def winfo_class(self):
        return self._wclass

    def winfo_name(self):
        return self._name

    def winfo_parent(self):
        return self.master._w if self.master is not None else ""

    def winfo_id(self):
        return self._id

    def winfo_fpixels(self, n):
        return _px(n)

    def winfo_pixels(self, n):
        return int(_px(n))

    def winfo_rgb(self, c):
        r, g, b = _g.rgb_tuple(c)
        return (r * 257, g * 257, b * 257)

    def winfo_depth(self):
        return 24

    def winfo_cells(self):
        return 256

    def winfo_containing(self, x, y, displayof=0):
        return None

    def winfo_geometry(self):
        return "%dx%d+%d+%d" % (self.winfo_width(), self.winfo_height(), self.winfo_x(), self.winfo_y())

    def nametowidget(self, name):
        name = str(name)
        if name in _widgets_by_path:
            return _widgets_by_path[name]
        w = self
        if name.startswith("."):
            w = _get_default_root()
            name = name[1:]
        for part in [p for p in name.split(".") if p]:
            w = w.children[part]
        return w

    _nametowidget = nametowidget

    # ---- イベント

    def bind(self, sequence=None, func=None, add=None):
        if sequence is None:
            return [self._seq_text(k) for k in self._bindings]
        if func is None:
            return ""
        fid = _add_binding(self._bindings, sequence, func, add)
        self._refresh_mouse_flags()
        return fid

    def unbind(self, sequence, funcid=None):
        key = _parse_seq(sequence)
        if funcid is None:
            self._bindings.pop(key, None)
        else:
            self._bindings[key] = [x for x in self._bindings.get(key, []) if x[0] != funcid]
        self._refresh_mouse_flags()

    def bind_all(self, sequence=None, func=None, add=None):
        if func is None:
            return ""
        fid = _add_binding(_all_bindings, sequence, func, add)
        root = _default_root
        if root is not None:
            root._refresh_mouse_flags(all_windows=True)
        return fid

    def unbind_all(self, sequence):
        _all_bindings.pop(_parse_seq(sequence), None)

    def bind_class(self, className, sequence=None, func=None, add=None):
        if func is None:
            return ""
        return _add_binding(_class_bindings.setdefault(className, {}), sequence, func, add)

    def unbind_class(self, className, sequence):
        _class_bindings.get(className, {}).pop(_parse_seq(sequence), None)

    def bindtags(self, tagList=None):
        return (self._w, self._wclass, str(self._toplevel()), "all")

    @staticmethod
    def _seq_text(key):
        t, d, m = key
        return "<%s%s%s>" % ("".join(x + "-" for x in sorted(m)), t, "-" + d if d else "")

    def event_generate(self, sequence, **kw):
        etype, detail, mods = _parse_seq(sequence)
        ev = _g.Event(widget=self, type=etype, x=kw.get("x", 0), y=kw.get("y", 0),
                      keysym=kw.get("keysym", detail or "??"), data=kw.get("data"))
        _dispatch(self, etype, detail, ev, mods)

    def event_add(self, virtual, *sequences):
        pass

    def event_delete(self, virtual, *sequences):
        pass

    def event_info(self, virtual=None):
        return ()

    def _has_mouse_bindings(self):
        for table in (self._bindings, _all_bindings):
            for (t, _, _) in table:
                if t in ("ButtonPress", "ButtonRelease", "Motion", "MouseWheel"):
                    return True
        top = self._toplevel()
        if top is not None and top is not self:
            for (t, _, _) in top._bindings:
                if t in ("ButtonPress", "ButtonRelease", "Motion", "MouseWheel"):
                    return True
        return False

    def _update_mouse_flag(self):
        want = self._has_mouse_bindings()
        if want != self._mouse_flag:
            self._mouse_flag = want
            _g.bridge().send({"op": "set", "id": self._id, "props": {"mouse": want}})

    def _refresh_mouse_flags(self, all_windows=False):
        if isinstance(self, Wm) or all_windows:
            stack = [self if not all_windows else _default_root]
            while stack:
                w = stack.pop()
                if w is None:
                    continue
                w._update_mouse_flag()
                stack.extend(w.winfo_children())
        else:
            self._update_mouse_flag()

    def _on_event(self, ev):
        kind = ev.get("ev")
        if kind == "mouse":
            self._on_mouse(ev)
        elif kind == "configure":
            self._size = (ev.get("w", 0), ev.get("h", 0))
            self._pos = (ev.get("x", 0), ev.get("y", 0))
            e = _g.Event(widget=self, type="Configure", width=int(self._size[0]),
                         height=int(self._size[1]), x=self._pos[0], y=self._pos[1])
            _dispatch(self, "Configure", None, e)
        elif kind == "command":
            self._on_command(ev)
        elif kind == "value":
            self._on_value(ev.get("value"), ev)
        elif kind == "submit":
            top = self._toplevel()
            if top is not None:
                top._focus = self
            for t in ("KeyPress", "KeyRelease"):
                e = _g.Event(widget=self, type=t, keysym="Return", char="\r", keycode=13)
                _dispatch(self, t, "Return", e)
        elif kind == "focus":
            top = self._toplevel()
            if ev.get("in"):
                if top is not None:
                    top._focus = self
                _dispatch(self, "FocusIn", None, _g.Event(widget=self, type="FocusIn"))
            else:
                _dispatch(self, "FocusOut", None, _g.Event(widget=self, type="FocusOut"))
        else:
            self._on_other(ev)

    def _on_other(self, ev):
        pass

    def _on_command(self, ev):
        pass

    def _on_value(self, value, ev):
        pass

    def _mouse_event(self, ev, etype):
        mods = set(ev.get("mods") or ())
        btn = ev.get("btn", 1)
        if etype == "Motion" and ev.get("pressed", True):
            mods.add("B%d" % btn)
        if ev.get("type") == "double":
            mods.add("Double")
        x, y = ev.get("x", 0), ev.get("y", 0)
        state = (4 if "Control" in mods else 0) | (1 if "Shift" in mods else 0) | \
                (256 if "B1" in mods else 0)
        e = _g.Event(widget=self, type=etype, x=int(x), y=int(y), x_root=int(x), y_root=int(y),
                     num=btn if etype != "Motion" else "??", state=state,
                     delta=int(ev.get("delta", 0)))
        return e, frozenset(mods)

    def _on_mouse(self, ev):
        t = ev.get("type")
        etype = {"press": "ButtonPress", "double": "ButtonPress", "release": "ButtonRelease",
                 "motion": "Motion", "wheel": "MouseWheel"}.get(t)
        if etype is None:
            return
        e, mods = self._mouse_event(ev, etype)
        detail = str(ev.get("btn", 1)) if etype in ("ButtonPress", "ButtonRelease") else None
        _dispatch(self, etype, detail, e, mods)

    # ---- フォーカス

    def focus_set(self):
        top = self._toplevel()
        if top is not None:
            top._focus = self
        _g.bridge().send({"op": "focus", "id": self._id})

    focus = focus_set
    focus_force = focus_set

    def focus_get(self):
        top = self._toplevel()
        return getattr(top, "_focus", None) if top is not None else None

    focus_displayof = focus_get
    focus_lastfor = focus_get

    def tk_focusNext(self):
        return None

    def tk_focusPrev(self):
        return None

    def grab_set(self):
        pass

    grab_set_global = grab_set

    def grab_release(self):
        pass

    def grab_current(self):
        return None

    def grab_status(self):
        return None

    # ---- 時間・ループ

    def after(self, ms, func=None, *args):
        if func is None:
            time.sleep(float(ms) / 1000.0)
            return None
        tid = _g.bridge().call_later(int(float(ms)), func, *args)
        return "after#%d" % tid

    def after_idle(self, func, *args):
        return self.after(0, func, *args)

    def after_cancel(self, id):
        if not id:
            return
        try:
            _g.bridge().cancel(int(str(id).split("#")[-1]))
        except ValueError:
            pass

    def update(self):
        _g.bridge().process_pending()

    def update_idletasks(self):
        _g.bridge().flush()

    def mainloop(self, n=0):
        b = _g.bridge()
        root = _default_root
        b.mainloop(stop=lambda: root is None or root._destroyed)

    def quit(self):
        _g.bridge().quit_all()

    def wait_window(self, window=None):
        w = window or self
        _g.bridge().mainloop(stop=lambda: w._destroyed)

    def wait_variable(self, name):
        var = name
        if not isinstance(var, Variable):
            return
        start = [var._value]
        changed = [False]

        def cb(*a):
            changed[0] = True
        cbname = var.trace_add("write", cb)
        try:
            _g.bridge().mainloop(stop=lambda: changed[0])
        finally:
            var.trace_remove("write", cbname)

    waitvar = wait_variable

    def wait_visibility(self, window=None):
        _g.bridge().flush()

    # ---- その他

    def bell(self, displayof=0):
        _g.bridge().send({"op": "bell"})

    def clipboard_clear(self, **kw):
        self._clip = ""

    def clipboard_append(self, string, **kw):
        self._clip = getattr(self, "_clip", "") + str(string)
        _g.bridge().send({"op": "clipboard", "text": self._clip})

    def clipboard_get(self, **kw):
        v = _g.bridge().request({"op": "clipboard_get"})
        if v is None:
            raise TclError("CLIPBOARD selection doesn't exist or form \"STRING\" not defined")
        return v

    def selection_get(self, **kw):
        return self.clipboard_get()

    def option_add(self, pattern, value, priority=None):
        pass

    def option_get(self, name, className):
        return ""

    def option_clear(self):
        pass

    def option_readfile(self, fileName, priority=None):
        pass

    def register(self, func, subst=None, needcleanup=1):
        name = "py%d%s" % (id(func), getattr(func, "__name__", "f"))
        _registered[name] = func
        return name

    _register = register

    def deletecommand(self, name):
        _registered.pop(name, None)

    def getvar(self, name="PY_VAR"):
        return ""

    def setvar(self, name="PY_VAR", value="1"):
        pass

    def getint(self, s):
        return int(s)

    def getdouble(self, s):
        return float(s)

    def getboolean(self, s):
        return _bool(s)

    def report_callback_exception(self, exc, val, tb):
        import traceback
        sys.stderr.write("Exception in Tkinter callback\n")
        traceback.print_exception(exc, val, tb)

    def image_names(self):
        return image_names()

    def lift(self, aboveThis=None):
        if self.master is not None and isinstance(self.master, Misc) and not isinstance(self, Wm):
            pass
        _g.bridge().send({"op": "raise", "id": self._id})

    tkraise = lift

    def lower(self, belowThis=None):
        _g.bridge().send({"op": "lower", "id": self._id})

    def tk_setPalette(self, *args, **kw):
        pass

    def tk_bisque(self):
        pass

    def tk_strictMotif(self, boolean=None):
        return 0

    # ---- 片付け

    def destroy(self):
        if self._destroyed:
            return
        for c in list(self.children.values()):
            c._destroy_quiet()
        self._destroyed = True
        _dispatch(self, "Destroy", None, _g.Event(widget=self, type="Destroy"))
        b = _g.bridge()
        b.handlers.pop(self._id, None)
        b.send({"op": "destroy", "id": self._id})
        self._detach()

    def _destroy_quiet(self):
        """親と一緒に消える時(Android 側は親を消すと子も消える)。"""
        if self._destroyed:
            return
        for c in list(self.children.values()):
            c._destroy_quiet()
        self._destroyed = True
        _g.bridge().handlers.pop(self._id, None)
        _widgets_by_path.pop(self._w, None)

    def _detach(self):
        _widgets_by_path.pop(self._w, None)
        m = self.master
        if m is not None:
            if m.children.get(self._name) is self:
                del m.children[self._name]
            if self in m._pack_list:
                m._pack_list.remove(self)


_registered = {}


# ===================================================================== 配置(pack / grid / place)

class Pack:
    def pack_configure(self, cnf={}, **kw):
        kw = _cnfmerge((cnf, kw))
        opts = dict(self._pack or {"side": "top", "fill": "none", "expand": False,
                                   "padx": [0, 0], "pady": [0, 0], "ipadx": 0, "ipady": 0,
                                   "anchor": "center"})
        for k, v in kw.items():
            k = k.rstrip("_")
            if k in ("padx", "pady"):
                opts[k] = _pair(v)
            elif k in ("ipadx", "ipady"):
                opts[k] = _px(v)
            elif k == "expand":
                opts[k] = _bool(v)
            elif k in ("side", "fill", "anchor"):
                opts[k] = str(v)
            else:
                opts[k] = v
        master = self.master
        before, after = opts.pop("before", None), opts.pop("after", None)
        opts.pop("in", None)
        self._grid = self._place = None
        self._pack = opts
        lst = master._pack_list
        if self in lst and (before is not None or after is not None):
            lst.remove(self)
        if self not in lst:
            if before is not None and before in lst:
                lst.insert(lst.index(before), self)
            elif after is not None and after in lst:
                lst.insert(lst.index(after) + 1, self)
            else:
                lst.append(self)
        b = _g.bridge()
        b.send({"op": "layout", "id": self._id, "opts": dict(opts, m="pack")})
        b.send({"op": "order", "id": master._id, "ids": [w._id for w in lst]})

    pack = configure_pack = pack_configure

    def pack_forget(self):
        if self._pack is None:
            return
        self._pack = None
        if self in self.master._pack_list:
            self.master._pack_list.remove(self)
        _g.bridge().send({"op": "layout", "id": self._id, "opts": {"m": "none"}})

    forget = pack_forget

    def pack_info(self):
        return dict(self._pack or {}, **{"in": self.master})

    info = pack_info

    def pack_propagate(self, flag=None):
        if flag is None:
            return self._propagate
        self._propagate = _bool(flag)
        _g.bridge().send({"op": "set", "id": self._id, "props": {"propagate": self._propagate}})

    propagate = pack_propagate

    def pack_slaves(self):
        return list(self._pack_list)

    slaves = pack_slaves


class Grid:
    def grid_configure(self, cnf={}, **kw):
        kw = _cnfmerge((cnf, kw))
        opts = dict(self._grid or getattr(self, "_grid_saved", None) or
                    {"padx": [0, 0], "pady": [0, 0], "ipadx": 0, "ipady": 0, "sticky": "",
                     "rowspan": 1, "columnspan": 1})
        for k, v in kw.items():
            k = k.rstrip("_")
            if k in ("padx", "pady"):
                opts[k] = _pair(v)
            elif k in ("ipadx", "ipady"):
                opts[k] = _px(v)
            elif k in ("row", "column", "rowspan", "columnspan"):
                opts[k] = int(v)
            elif k == "sticky":
                opts[k] = "".join(str(v).replace(",", "").replace(" ", "")).lower()
            else:
                opts[k] = v
        master = self.master
        if "column" not in opts:
            opts["column"] = 0
        if "row" not in opts:
            used = [w._grid["row"] + w._grid.get("rowspan", 1) for w in master.grid_slaves()
                    if w is not self and w._grid is not None]
            opts["row"] = max(used) if used else 0
        self._pack = self._place = None
        if self in master._pack_list:
            master._pack_list.remove(self)
        self._grid = opts
        o = {"m": "grid", "row": opts["row"], "col": opts["column"],
             "rowspan": opts.get("rowspan", 1), "colspan": opts.get("columnspan", 1),
             "sticky": opts.get("sticky", ""), "padx": opts["padx"], "pady": opts["pady"],
             "ipadx": opts["ipadx"], "ipady": opts["ipady"]}
        _g.bridge().send({"op": "layout", "id": self._id, "opts": o})

    grid = grid_configure

    def grid_forget(self):
        if self._grid is None:
            return
        self._grid = None
        _g.bridge().send({"op": "layout", "id": self._id, "opts": {"m": "none"}})

    def grid_remove(self):
        self._grid_saved = self._grid
        self.grid_forget()

    def grid_info(self):
        return dict(self._grid or {}, **{"in": self.master})

    def grid_slaves(self, row=None, column=None):
        out = []
        for c in self.winfo_children():
            g = c._grid
            if g is None:
                continue
            if row is not None and g["row"] != row:
                continue
            if column is not None and g["column"] != column:
                continue
            out.append(c)
        return out[::-1]

    def grid_size(self):
        cols = rows = 0
        for c in self.grid_slaves():
            g = c._grid
            cols = max(cols, g["column"] + g.get("columnspan", 1))
            rows = max(rows, g["row"] + g.get("rowspan", 1))
        return (cols, rows)

    size = grid_size

    def grid_bbox(self, column=None, row=None, col2=None, row2=None):
        return (0, 0, self.winfo_width(), self.winfo_height())

    bbox_grid = grid_bbox

    def grid_propagate(self, flag=None):
        return self.pack_propagate(flag)

    def _grid_config(self, table, key, index, cnf, kw):
        kw = _cnfmerge((cnf, kw))
        idxs = index if isinstance(index, (tuple, list)) else [index]
        if index == "all":
            idxs = range(self.grid_size()[0 if key == "cols" else 1])
        if not kw:
            i = int(idxs[0]) if idxs else 0
            return dict(table.get(i, {"weight": 0, "minsize": 0, "pad": 0, "uniform": None}))
        for i in idxs:
            i = int(i)
            d = table.setdefault(i, {"weight": 0, "minsize": 0, "pad": 0, "uniform": None})
            for k, v in kw.items():
                d[k] = v if k == "uniform" else _px(v)
        _g.bridge().send({"op": "set", "id": self._id, "props": {
            key: {str(i): [d["weight"], d["minsize"], d["pad"]] for i, d in table.items()}}})

    def grid_columnconfigure(self, index, cnf={}, **kw):
        return self._grid_config(self._cols, "cols", index, cnf, kw)

    def grid_rowconfigure(self, index, cnf={}, **kw):
        return self._grid_config(self._rows, "rows", index, cnf, kw)

    columnconfigure = grid_columnconfigure
    rowconfigure = grid_rowconfigure

    def grid_location(self, x, y):
        return (0, 0)

    def grid_anchor(self, anchor=None):
        pass


class Place:
    def place_configure(self, cnf={}, **kw):
        kw = _cnfmerge((cnf, kw))
        opts = dict(self._place or {"anchor": "nw"})
        for k, v in kw.items():
            k = k.rstrip("_")
            if k in ("x", "y", "width", "height"):
                opts[k] = _px(v)
            elif k in ("relx", "rely", "relwidth", "relheight"):
                opts[k] = float(v)
            else:
                opts[k] = v
        master = self.master
        self._pack = self._grid = None
        if self in master._pack_list:
            master._pack_list.remove(self)
        self._place = opts
        o = {"m": "place", "x": opts.get("x", 0), "y": opts.get("y", 0),
             "relx": opts.get("relx", 0), "rely": opts.get("rely", 0),
             "anchor": str(opts.get("anchor", "nw"))}
        for a, b in (("width", "w"), ("height", "h"), ("relwidth", "relw"), ("relheight", "relh")):
            if a in opts and opts[a] is not None:
                o[b] = opts[a]
        _g.bridge().send({"op": "layout", "id": self._id, "opts": o})

    place = place_configure

    def place_forget(self):
        if self._place is None:
            return
        self._place = None
        _g.bridge().send({"op": "layout", "id": self._id, "opts": {"m": "none"}})

    def place_info(self):
        return dict(self._place or {}, **{"in": self.master})

    def place_slaves(self):
        return [c for c in self.winfo_children() if c._place is not None]


# ===================================================================== 窓

class Wm:
    _title = ""

    def _wm_init(self):
        self._title = self._title or ""
        self._geometry = None
        self._protocols = {}
        self._menu = None
        self._focus = None
        self._attrs = {}
        self._state = "normal"
        self._resizable = (True, True)

    def _window_props(self):
        p = {"title": self._title}
        bg = _g.color(self._opts.get("background"))
        if bg:
            p["bg"] = bg
        if self._menu is not None:
            p["menu"] = self._menu._json()
        if self._geometry:
            m = re.match(r"^(\d+)x(\d+)", self._geometry)
            if m:
                p["width"], p["height"] = int(m.group(1)), int(m.group(2))
        if self._opts.get("width"):
            p.setdefault("width", _px(self._opts.get("width")))
        if self._opts.get("height"):
            p.setdefault("height", _px(self._opts.get("height")))
        return p

    def wm_title(self, string=None):
        if string is None:
            return self._title
        self._title = str(string)
        _g.bridge().send({"op": "set", "id": self._id, "props": {"title": self._title}})

    title = wm_title

    def wm_geometry(self, newGeometry=None):
        if newGeometry is None:
            return "%dx%d+0+0" % (self.winfo_width(), self.winfo_height())
        self._geometry = str(newGeometry)
        m = re.match(r"^(\d+)x(\d+)", self._geometry)
        if m:
            self._size = (int(m.group(1)), int(m.group(2)))
            _g.bridge().send({"op": "set", "id": self._id, "props": {"width": self._size[0],
                                                                       "height": self._size[1]}})

    geometry = wm_geometry

    def wm_protocol(self, name=None, func=None):
        if func is None:
            return self._protocols.get(name, "")
        self._protocols[name] = func

    protocol = wm_protocol

    def wm_resizable(self, width=None, height=None):
        if width is None:
            return self._resizable
        self._resizable = (_bool(width), _bool(height if height is not None else width))

    resizable = wm_resizable

    def wm_attributes(self, *args, **kw):
        if len(args) == 1:
            return self._attrs.get(str(args[0]).lstrip("-"), "")
        for i in range(0, len(args) - 1, 2):
            self._attrs[str(args[i]).lstrip("-")] = args[i + 1]
        self._attrs.update(kw)
        return ""

    attributes = wm_attributes

    def wm_state(self, newstate=None):
        if newstate is None:
            return self._state
        self._state = newstate
        if newstate == "normal":
            self.deiconify()
        elif newstate in ("withdrawn", "iconic"):
            self.withdraw()

    state = wm_state

    def wm_withdraw(self):
        self._state = "withdrawn"
        _g.bridge().send({"op": "set", "id": self._id, "props": {"visible": False}})

    withdraw = wm_withdraw

    def wm_iconify(self):
        self._state = "iconic"

    iconify = wm_iconify

    def wm_deiconify(self):
        self._state = "normal"
        _g.bridge().send({"op": "set", "id": self._id, "props": {"visible": True}})
        _g.bridge().send({"op": "raise", "id": self._id})

    deiconify = wm_deiconify

    def _noop(self, *a, **kw):
        return ""

    wm_iconbitmap = iconbitmap = wm_iconphoto = iconphoto = wm_iconname = iconname = _noop
    wm_minsize = minsize = wm_maxsize = maxsize = wm_aspect = aspect = _noop
    wm_overrideredirect = overrideredirect = wm_transient = transient = _noop
    wm_positionfrom = positionfrom = wm_sizefrom = sizefrom = wm_group = group = _noop
    wm_client = client = wm_command = command = wm_focusmodel = focusmodel = _noop
    wm_iconmask = iconmask = wm_iconposition = iconposition = wm_iconwindow = iconwindow = _noop
    wm_colormapwindows = colormapwindows = wm_frame = frame = wm_grid = _noop
    wm_manage = manage = wm_forget = _noop

    def _close_requested(self):
        fn = self._protocols.get("WM_DELETE_WINDOW")
        if fn:
            _g.bridge()._call(fn)
        else:
            self.destroy()

    def _on_other(self, ev):
        kind = ev.get("ev")
        if kind == "close":
            self._close_requested()
        elif kind == "key":
            self._on_key(ev)
        elif kind == "menu":
            key = str(ev.get("key", ""))
            mid, _, idx = key.partition(":")
            menu = _menus.get(int(mid)) if mid.isdigit() else None
            if menu is not None and idx.isdigit():
                menu.invoke(int(idx))

    def _on_key(self, ev):
        target = self._focus if self._focus is not None and not self._focus._destroyed else self
        etype = "KeyPress" if ev.get("type", "press") == "press" else "KeyRelease"
        keysym = ev.get("keysym") or "??"
        ch = ev.get("char") or _KEYSYM_CHARS.get(keysym, "")
        mods = frozenset(ev.get("mods") or ())
        state = (4 if "Control" in mods else 0) | (1 if "Shift" in mods else 0) | (8 if "Alt" in mods else 0)
        e = _g.Event(widget=target, type=etype, keysym=keysym, char=ch,
                     keycode=int(ev.get("code", 0)), keysym_num=ord(ch) if len(ch) == 1 else 0,
                     state=state)
        _dispatch(target, etype, keysym, e, mods)


class Tk(Misc, Wm, Pack, Grid, Place):
    _type = "window"
    _wclass = "Tk"
    _container = True

    def __init__(self, screenName=None, baseName=None, className="Tk", useTk=True, sync=False, use=None):
        global _default_root
        self._setup(None, {}, {})
        self._wclass = className
        self._wm_init()
        name = baseName or os.path.splitext(os.path.basename(sys.argv[0] if sys.argv and sys.argv[0] else "tk"))[0]
        self._title = name or "tk"
        if _support_default_root and _default_root is None:
            _default_root = self
        self._create(None)

    _on_other = Wm._on_other

    def _props(self):
        return self._window_props()

    def destroy(self):
        global _default_root
        Misc.destroy(self)
        if _default_root is self:
            _default_root = None
            _g.bridge().quit_all()
            _g.bridge().flush()

    def readprofile(self, baseName, className):
        pass

    def loadtk(self):
        pass

    def __getattr__(self, attr):
        if attr.startswith("_"):
            raise AttributeError(attr)
        return getattr(self.tk, attr)

    def _set_opt(self, key, value):
        if key == "menu":
            self._set_menu(value)
        Misc._set_opt(self, key, value)

    def _set_menu(self, menu):
        if self._menu is not None:
            self._menu._owners.discard(self)
        self._menu = menu if isinstance(menu, Menu) else None
        if self._menu is not None:
            self._menu._owners.add(self)


def Tcl(screenName=None, baseName=None, className="Tk", useTk=False):
    return Tk(screenName, baseName, className, useTk)


# ===================================================================== 部品

class BaseWidget(Misc):
    def __init__(self, master=None, cnf={}, *args, **kw):
        # Widget(master, cnf, **kw) と、昔の Widget(master, widgetName, cnf, kw) の両方を受ける
        if isinstance(cnf, str):
            cnf, kw = (args[0] if args else {}), dict(args[1] if len(args) > 1 else {}, **kw)
        if master is None:
            master = _get_default_root()
        self._setup(master, cnf, kw)
        self._init()
        self._create(self._parent_id())

    def _init(self):
        pass

    def _parent_id(self):
        return self.master._id

    def destroy(self):
        Misc.destroy(self)


class Widget(BaseWidget, Pack, Place, Grid):
    pass


class Toplevel(BaseWidget, Wm):
    _type = "window"
    _wclass = "Toplevel"
    _container = True

    def __init__(self, master=None, cnf={}, **kw):
        kw = _cnfmerge((cnf, kw))
        if master is None:
            master = _get_default_root()
        self._setup(master, {}, kw)
        self._wm_init()
        self._title = master._toplevel()._title if master._toplevel() else ""
        self._create(None)

    _on_other = Wm._on_other

    def _props(self):
        p = self._window_props()
        p["toplevel"] = True
        return p

    def _set_opt(self, key, value):
        if key == "menu":
            Tk._set_menu(self, value)
        Misc._set_opt(self, key, value)


class Frame(Widget):
    _type = "frame"
    _wclass = "Frame"
    _container = True

    def _props(self):
        p = self._style_props({})
        p.pop("fg", None)
        for k in ("width", "height"):
            if self._opts.get(k):
                p[k] = _px(self._opts[k])
        padx, pady = _px(self._opts.get("padx")), _px(self._opts.get("pady"))
        if padx or pady:
            p["padding"] = [padx, pady, padx, pady]
        return p


class LabelFrame(Frame):
    _type = "labelframe"
    _wclass = "Labelframe"

    def _props(self):
        p = Frame._props(self)
        p["text"] = self._text_value()
        fg = _g.color(self._opts.get("foreground"))
        if fg:
            p["fg"] = fg
        return p


Labelframe = LabelFrame


class _TextWidget(Widget):
    """Label / Button などの文字と画像を出す部品。width は文字数(画像がある時はピクセル)。"""
    _type = "label"

    def _props(self):
        p = self._style_props({})
        p["text"] = self._text_value()
        img = self._opts.get("image")
        if isinstance(img, str) and img in _images:
            img = _images[img]
        if isinstance(img, Image):
            ref = img._ref()
            if ref:
                p["image"] = ref
                p["compound"] = str(self._opts.get("compound", "none") or "none")
        w = self._opts.get("width")
        if w not in (None, "", 0):
            if "image" in p:
                p["width"] = _px(w)
            else:
                p["wchars"] = int(_px(w))
        h = self._opts.get("height")
        if h not in (None, "", 0):
            if "image" in p:
                p["height"] = _px(h)
            else:
                p["hlines"] = int(_px(h))
        for k in ("anchor", "justify"):
            if self._opts.get(k):
                p[k] = str(self._opts[k])
        wl = self._opts.get("wraplength")
        if wl:
            p["wrap"] = _px(wl)
        padx, pady = _px(self._opts.get("padx")), _px(self._opts.get("pady"))
        if padx or pady:
            p["padding"] = [padx, pady, padx, pady]
        if self._opts.get("underline", -1) not in (-1, None, ""):
            pass
        return p


class Label(_TextWidget):
    _type = "label"
    _wclass = "Label"


class Message(_TextWidget):
    _type = "label"
    _wclass = "Message"

    def _props(self):
        p = _TextWidget._props(self)
        p.pop("wchars", None)
        p["wrap"] = _px(self._opts.get("width"), 0) or 240
        return p


class Button(_TextWidget):
    _type = "button"
    _wclass = "Button"

    def _on_command(self, ev):
        self.invoke()

    def invoke(self):
        if str(self._opts.get("state", "normal")) == "disabled":
            return None
        cmd = self._opts.get("command")
        if cmd:
            return _g.bridge()._call(_resolve(cmd))
        return None

    def flash(self):
        pass


def _resolve(cmd):
    if isinstance(cmd, str):
        return _registered.get(cmd, lambda *a: None)
    return cmd


class Checkbutton(_TextWidget):
    _type = "check"
    _wclass = "Checkbutton"
    _defaults = {"onvalue": 1, "offvalue": 0}

    def _init(self):
        if self._opts.get("variable") is None:
            self._opts["variable"] = IntVar(value=0) if isinstance(self._opts.get("onvalue"), int) \
                else StringVar(value=self._opts.get("offvalue"))
            self._var_links()

    def _is_on(self):
        v = self._opts.get("variable")
        if not isinstance(v, Variable):
            return False
        return str(v._value) == str(self._opts.get("onvalue", 1)) or \
            (isinstance(v, BooleanVar) and _bool(v._value) and _bool(self._opts.get("onvalue", 1)))

    def _props(self):
        p = _TextWidget._props(self)
        p["value"] = self._is_on()
        return p

    def _on_value(self, value, ev):
        v = self._opts.get("variable")
        if isinstance(v, Variable):
            v.set(self._opts.get("onvalue", 1) if value else self._opts.get("offvalue", 0))
        cmd = self._opts.get("command")
        if cmd:
            _g.bridge()._call(_resolve(cmd))

    def select(self):
        self._opts["variable"].set(self._opts.get("onvalue", 1))

    def deselect(self):
        self._opts["variable"].set(self._opts.get("offvalue", 0))

    def toggle(self):
        if self._is_on():
            self.deselect()
        else:
            self.select()

    def invoke(self):
        self.toggle()
        cmd = self._opts.get("command")
        return _resolve(cmd)() if cmd else None

    def flash(self):
        pass


class Radiobutton(_TextWidget):
    _type = "radio"
    _wclass = "Radiobutton"
    _defaults = {"value": ""}

    def _init(self):
        if self._opts.get("variable") is None:
            self._opts["variable"] = _shared_radio_var()
            self._var_links()

    def _props(self):
        p = _TextWidget._props(self)
        v = self._opts.get("variable")
        p["value"] = isinstance(v, Variable) and str(v._value) == str(self._opts.get("value", ""))
        return p

    def _on_value(self, value, ev):
        if value:
            self.invoke()

    def select(self):
        self._opts["variable"].set(self._opts.get("value", ""))

    def deselect(self):
        self._opts["variable"].set("")

    def invoke(self):
        self.select()
        cmd = self._opts.get("command")
        return _g.bridge()._call(_resolve(cmd)) if cmd else None

    def flash(self):
        pass


_radio_var = None


def _shared_radio_var():
    global _radio_var
    if _radio_var is None:
        _radio_var = StringVar(value="")
    return _radio_var


class Entry(Widget):
    _type = "entry"
    _wclass = "Entry"

    def _init(self):
        tv = self._opts.get("textvariable")
        self._text = str(tv.get()) if isinstance(tv, Variable) else ""
        self._cursor = len(self._text)

    def _var_changed(self, var):
        if var is self._opts.get("textvariable"):
            self._text = str(var._value)
        self._send_props()

    def _props(self):
        p = self._style_props({})
        p["value"] = self._text
        p["wchars"] = int(_px(self._opts.get("width"), 20)) or 20
        if self._opts.get("show"):
            p["password"] = True
        if self._opts.get("justify"):
            p["justify"] = str(self._opts["justify"])
        return p

    def _set_text(self, value, from_android=False):
        old = self._text
        if value == old:
            return
        if not from_android or not self._validate(value, old):
            if from_android:
                self._send_props()  # 検証で拒否 → 元に戻す
                return
        self._text = value
        tv = self._opts.get("textvariable")
        if isinstance(tv, Variable) and str(tv._value) != value:
            tv._value = value
            tv._fire("write")
            for w in list(tv._links):
                if w is not self:
                    w._var_changed(tv)
        if not from_android:
            self._send_props()

    def _validate(self, new, old):
        mode = str(self._opts.get("validate", "none"))
        vcmd = self._opts.get("validatecommand") or self._opts.get("vcmd")
        if mode in ("none", "") or not vcmd:
            return True
        if mode not in ("key", "all"):
            return True
        if isinstance(vcmd, (tuple, list)):
            fn, args = _resolve(vcmd[0]), list(vcmd[1:])
        else:
            fn, args = _resolve(vcmd), []
        inserted = new[len(old):] if new.startswith(old) else new
        subst = {"%P": new, "%s": old, "%S": inserted, "%d": "1" if len(new) >= len(old) else "0",
                 "%i": str(len(old)), "%v": mode, "%V": "key", "%W": self._w}
        try:
            r = fn(*[subst.get(a, a) for a in args])
        except Exception:
            import traceback
            traceback.print_exc()
            return True
        return r is None or _bool(r)

    def _on_value(self, value, ev):
        old = self._text
        self._set_text(str(value), from_android=True)
        if self._text != old:
            ch = self._text[len(old):len(old) + 1] if self._text.startswith(old) else ""
            ks = ch if ch and ch.isalnum() else ("BackSpace" if len(self._text) < len(old) else "??")
            for t in ("KeyPress", "KeyRelease"):
                _dispatch(self, t, ks, _g.Event(widget=self, type=t, char=ch, keysym=ks))

    def get(self):
        return self._text

    def _index(self, index):
        if index in (END, "end"):
            return len(self._text)
        if index in (INSERT, "insert"):
            return min(self._cursor, len(self._text))
        if index in ("anchor", "sel.first"):
            return 0
        if index == "sel.last":
            return len(self._text)
        try:
            return max(0, min(len(self._text), int(index)))
        except (TypeError, ValueError):
            return len(self._text)

    def index(self, index):
        return self._index(index)

    def insert(self, index, string):
        if str(self._opts.get("state", "normal")) == "disabled":
            return
        i = self._index(index)
        self._set_text(self._text[:i] + str(string) + self._text[i:])
        self._cursor = i + len(str(string))

    def delete(self, first, last=None):
        if str(self._opts.get("state", "normal")) == "disabled":
            return
        a = self._index(first)
        b = a + 1 if last is None else self._index(last)
        self._set_text(self._text[:a] + self._text[max(a, b):])
        self._cursor = a

    def icursor(self, index):
        self._cursor = self._index(index)

    def select_range(self, start, end):
        pass

    selection_range = select_range

    def select_clear(self):
        pass

    selection_clear = select_clear

    def select_present(self):
        return False

    selection_present = select_present

    def select_adjust(self, index):
        pass

    def select_from(self, index):
        pass

    def select_to(self, index):
        pass

    def xview(self, *args):
        return (0.0, 1.0)

    xview_moveto = xview_scroll = xview

    def scan_mark(self, x):
        pass

    def scan_dragto(self, x):
        pass

    def validate(self):
        return self._validate(self._text, self._text)


class Spinbox(Entry):
    _type = "spin"
    _wclass = "Spinbox"
    _defaults = {"from": 0, "to": 0, "increment": 1}

    def _init(self):
        Entry._init(self)
        if self._opts.get("from_") is not None:
            self._opts["from"] = self._opts.pop("from_")
        if not self._text:
            vals = self._values()
            self._text = str(vals[0]) if vals else self._fmt(_px(self._opts.get("from", 0)))
            tv = self._opts.get("textvariable")
            if isinstance(tv, Variable) and tv._value in ("", None):
                tv._value = self._text

    def _set_opt(self, key, value):
        if key == "from_":
            key = "from"
        Misc._set_opt(self, key, value)

    def _values(self):
        v = self._opts.get("values")
        if not v:
            return None
        return list(v) if isinstance(v, (tuple, list)) else str(v).split()

    def _fmt(self, n):
        fmt = self._opts.get("format")
        if fmt:
            try:
                return fmt % n
            except (TypeError, ValueError):
                pass
        inc = float(self._opts.get("increment", 1))
        return str(int(n)) if float(inc).is_integer() and float(n).is_integer() else str(round(n, 6))

    def _props(self):
        p = Entry._props(self)
        p["wchars"] = int(_px(self._opts.get("width"), 8)) or 8
        return p

    def _on_command(self, ev):
        self._step(1 if ev.get("dir", "up") == "up" else -1)

    def _step(self, d):
        vals = self._values()
        if vals:
            try:
                i = vals.index(self._text)
            except ValueError:
                i = -1
            n = i + d
            if self._opts.get("wrap"):
                n %= len(vals)
            n = max(0, min(len(vals) - 1, n))
            self._set_text(str(vals[n]))
        else:
            lo, hi = float(self._opts.get("from", 0)), float(self._opts.get("to", 0))
            inc = float(self._opts.get("increment", 1))
            try:
                cur = float(self._text)
            except ValueError:
                cur = lo
            n = cur + inc * d
            if n > hi:
                n = lo if self._opts.get("wrap") else hi
            if n < lo:
                n = hi if self._opts.get("wrap") else lo
            self._set_text(self._fmt(n))
        cmd = self._opts.get("command")
        if cmd:
            _g.bridge()._call(_resolve(cmd))

    def invoke(self, element):
        self._step(1 if element == "buttonup" else -1)

    def set(self, value):
        self._set_text(str(value))


class Text(Widget):
    _type = "text"
    _wclass = "Text"

    def _init(self):
        self._s = ""
        self._marks = {"insert": 0, "current": 0}
        self._tags = {}  # name -> {"opts": {}, "ranges": [[a, b]]}
        self._tag_order = []
        self._undo = []

    def _props(self):
        p = self._style_props({})
        p["value"] = self._s
        p["wchars"] = int(_px(self._opts.get("width"), 40)) or 40
        p["hlines"] = int(_px(self._opts.get("height"), 12)) or 12
        if str(self._opts.get("wrap", "char")) == "none":
            p["nowrap"] = True
        spans = []
        for name in self._tag_order:
            t = self._tags.get(name)
            if not t or not t["ranges"]:
                continue
            o = t["opts"]
            style = {}
            fg = _g.color(o.get("foreground") or o.get("fg"))
            bg = _g.color(o.get("background") or o.get("bg"))
            f = _g.font(o.get("font"))
            if fg:
                style["fg"] = fg
            if bg:
                style["bg"] = bg
            if f:
                style["font"] = f
            if o.get("underline"):
                style["underline"] = True
            if o.get("overstrike"):
                style["strike"] = True
            if o.get("justify"):
                style["justify"] = str(o["justify"])
            if o.get("elide"):
                style["hidden"] = True
            if style:
                for a, b in t["ranges"]:
                    if b > a:
                        spans.append([a, b, style])
        if spans:
            p["spans"] = spans
        return p

    def _disabled(self):
        return str(self._opts.get("state", "normal")) == "disabled"

    # ---- 位置

    def _line_starts(self):
        starts = [0]
        for i, ch in enumerate(self._s):
            if ch == "\n":
                starts.append(i + 1)
        return starts

    def _pos(self, index):
        s = self._s
        full_end = len(s) + 1
        index = str(index).strip()
        m = re.match(r"^([^+\-\s]+)\s*(.*)$", index)
        base, rest = (m.group(1), m.group(2)) if m else (index, "")
        if re.match(r"^\d+\.\d+$", base):
            line, col = base.split(".")
            starts = self._line_starts()
            li = int(line) - 1
            if li < 0:
                pos = 0
            elif li >= len(starts):
                pos = full_end
            else:
                line_end = starts[li + 1] - 1 if li + 1 < len(starts) else len(s)
                pos = min(starts[li] + int(col), line_end)
        elif re.match(r"^\d+\.end$", base):
            starts = self._line_starts()
            li = int(base.split(".")[0]) - 1
            if li >= len(starts):
                pos = len(s)
            else:
                pos = starts[li + 1] - 1 if li + 1 < len(starts) else len(s)
        elif base == "end":
            pos = full_end
        elif base in self._marks:
            pos = self._marks[base]
        elif base.startswith("@"):
            pos = len(s)
        elif base in ("sel.first", "sel.last"):
            raise TclError('text doesn\'t contain any characters tagged with "sel"')
        elif "." in base and base.split(".")[0] in self._tags:
            name, which = base.rsplit(".", 1)
            rs = self._tags[name]["ranges"]
            if not rs:
                raise TclError('text doesn\'t contain any characters tagged with "%s"' % name)
            pos = rs[0][0] if which == "first" else rs[-1][1]
        else:
            try:
                pos = int(float(base))
            except ValueError:
                raise TclError('bad text index "%s"' % index)
        for mod in re.findall(r"([+\-])\s*(\d+)\s*([a-z]+)|(linestart|lineend|wordstart|wordend)", rest):
            sign, num, unit, word = mod
            if word:
                if word == "linestart":
                    pos = s.rfind("\n", 0, min(pos, len(s))) + 1
                elif word == "lineend":
                    e = s.find("\n", pos)
                    pos = len(s) if e < 0 else e
                elif word == "wordstart":
                    while pos > 0 and (s[pos - 1].isalnum() or s[pos - 1] == "_"):
                        pos -= 1
                elif word == "wordend":
                    while pos < len(s) and (s[pos].isalnum() or s[pos] == "_"):
                        pos += 1
                continue
            n = int(num) * (1 if sign == "+" else -1)
            if unit.startswith("c") or unit.startswith("i"):
                pos += n
            elif unit.startswith("l"):
                starts = self._line_starts()
                li = max(0, sum(1 for st in starts if st <= min(pos, len(s))) - 1)
                col = pos - starts[li]
                nl = max(0, min(len(starts) - 1, li + n))
                line_end = starts[nl + 1] - 1 if nl + 1 < len(starts) else len(s)
                pos = min(starts[nl] + col, line_end)
        return max(0, min(full_end, pos))

    def index(self, index):
        pos = self._pos(index)
        starts = self._line_starts()
        if pos > len(self._s):
            return "%d.0" % (len(starts) + 1)
        li = max(0, sum(1 for st in starts if st <= pos) - 1)
        return "%d.%d" % (li + 1, pos - starts[li])

    def compare(self, index1, op, index2):
        a, b = self._pos(index1), self._pos(index2)
        return {"<": a < b, "<=": a <= b, "==": a == b, ">=": a >= b, ">": a > b, "!=": a != b}[op]

    # ---- 中身

    def get(self, index1, index2=None):
        full = self._s + "\n"
        a = self._pos(index1)
        b = a + 1 if index2 is None else self._pos(index2)
        return full[a:b]

    def _shift(self, at, delta):
        """文字を足した・消した分だけ、マークとタグの範囲をずらす。"""
        if delta > 0:
            for k, v in self._marks.items():
                if v > at or (v == at and k == "insert"):
                    self._marks[k] = v + delta
            for t in self._tags.values():
                t["ranges"] = [[a + delta if a >= at else a, b + delta if b > at else b]
                               for a, b in t["ranges"]]
            return
        n = -delta
        end = at + n

        def f(v):
            return v if v <= at else (at if v <= end else v - n)
        for k, v in self._marks.items():
            self._marks[k] = f(v)
        for t in self._tags.values():
            t["ranges"] = [[f(a), f(b)] for a, b in t["ranges"] if f(b) > f(a)]

    def insert(self, index, chars, *args):
        """insert(位置, 文字, タグ, 文字2, タグ2, ...)"""
        if self._disabled():
            return
        off = min(self._pos(index), len(self._s))
        parts = [chars] + list(args)
        for i in range(0, len(parts), 2):
            text = str(parts[i])
            tags = parts[i + 1] if i + 1 < len(parts) else None
            self._s = self._s[:off] + text + self._s[off:]
            self._shift(off, len(text))
            if tags:
                for tg in (tags if isinstance(tags, (tuple, list)) else str(tags).split()):
                    self._tag_add_range(tg, off, off + len(text))
            off += len(text)
        self._send_props()

    def delete(self, index1, index2=None):
        if self._disabled():
            return
        a = min(self._pos(index1), len(self._s))
        b = a + 1 if index2 is None else min(self._pos(index2), len(self._s))
        if b <= a:
            return
        self._s = self._s[:a] + self._s[b:]
        self._shift(a, -(b - a))
        self._send_props()

    def replace(self, index1, index2, chars, *args):
        st = self._opts.get("state")
        self.delete(index1, index2)
        self.insert(index1, chars, *args)

    def _on_value(self, value, ev):
        self._s = str(value)
        self._marks["insert"] = len(self._s)
        for t in self._tags.values():
            t["ranges"] = [[a, min(b, len(self._s))] for a, b in t["ranges"] if a < len(self._s)]
        for t in ("KeyPress", "KeyRelease"):
            _dispatch(self, t, "??", _g.Event(widget=self, type=t))
        _dispatch(self, "Virtual", "Modified", _g.Event(widget=self, type="Virtual"))

    def see(self, index):
        _g.bridge().send({"op": "set", "id": self._id, "props": {"see": self._pos(index)}})

    def mark_set(self, markName, index):
        self._marks[markName] = min(self._pos(index), len(self._s))

    def mark_unset(self, *markNames):
        for m in markNames:
            if m not in ("insert", "current"):
                self._marks.pop(m, None)

    def mark_names(self):
        return tuple(self._marks)

    def mark_gravity(self, markName, direction=None):
        return "right"

    # ---- タグ

    def _tag(self, name):
        if name not in self._tags:
            self._tags[name] = {"opts": {}, "ranges": [], "binds": {}}
            self._tag_order.append(name)
        return self._tags[name]

    def _tag_add_range(self, name, a, b):
        t = self._tag(name)
        rs = t["ranges"] + [[a, b]]
        rs.sort()
        merged = []
        for r in rs:
            if merged and r[0] <= merged[-1][1]:
                merged[-1][1] = max(merged[-1][1], r[1])
            else:
                merged.append(list(r))
        t["ranges"] = merged

    def tag_add(self, tagName, index1, *args):
        idx = [index1] + list(args)
        if len(idx) == 1:
            idx.append(None)
        for i in range(0, len(idx), 2):
            a = self._pos(idx[i])
            b = a + 1 if i + 1 >= len(idx) or idx[i + 1] is None else self._pos(idx[i + 1])
            self._tag_add_range(tagName, a, min(b, len(self._s)))
        self._send_props()

    def tag_remove(self, tagName, index1, index2=None):
        if tagName not in self._tags:
            return
        a = self._pos(index1)
        b = a + 1 if index2 is None else self._pos(index2)
        out = []
        for x, y in self._tags[tagName]["ranges"]:
            if y <= a or x >= b:
                out.append([x, y])
            else:
                if x < a:
                    out.append([x, a])
                if y > b:
                    out.append([b, y])
        self._tags[tagName]["ranges"] = out
        self._send_props()

    def tag_delete(self, *tagNames):
        for n in tagNames:
            self._tags.pop(n, None)
            if n in self._tag_order:
                self._tag_order.remove(n)
        self._send_props()

    def tag_configure(self, tagName, cnf=None, **kw):
        kw = _cnfmerge((cnf, kw))
        t = self._tag(tagName)
        if not kw:
            return dict(t["opts"])
        t["opts"].update(kw)
        self._send_props()

    tag_config = tag_configure

    def tag_cget(self, tagName, option):
        return self._tag(tagName)["opts"].get(option, "")

    def tag_names(self, index=None):
        if index is None:
            return tuple(self._tag_order)
        p = self._pos(index)
        return tuple(n for n in self._tag_order if any(a <= p < b for a, b in self._tags[n]["ranges"]))

    def tag_ranges(self, tagName):
        out = []
        for a, b in self._tags.get(tagName, {"ranges": []})["ranges"]:
            out += [self.index(a), self.index(b)]
        return tuple(out)

    def tag_nextrange(self, tagName, index1, index2=None):
        a = self._pos(index1)
        for x, y in self._tags.get(tagName, {"ranges": []})["ranges"]:
            if y > a:
                return (self.index(max(x, a)), self.index(y))
        return ()

    def tag_prevrange(self, tagName, index1, index2=None):
        a = self._pos(index1)
        for x, y in reversed(self._tags.get(tagName, {"ranges": []})["ranges"]):
            if x < a:
                return (self.index(x), self.index(y))
        return ()

    def tag_raise(self, tagName, aboveThis=None):
        if tagName in self._tag_order:
            self._tag_order.remove(tagName)
            self._tag_order.append(tagName)

    def tag_lower(self, tagName, belowThis=None):
        if tagName in self._tag_order:
            self._tag_order.remove(tagName)
            self._tag_order.insert(0, tagName)

    def tag_bind(self, tagName, sequence, func, add=None):
        return _add_binding(self._tag(tagName)["binds"], sequence, func, add)

    def tag_unbind(self, tagName, sequence, funcid=None):
        self._tag(tagName)["binds"].pop(_parse_seq(sequence), None)

    def search(self, pattern, index, stopindex=None, forwards=None, backwards=None, exact=None,
               regexp=None, nocase=None, count=None, elide=None):
        full = self._s
        start = min(self._pos(index), len(full))
        flags = re.IGNORECASE if nocase else 0
        pat = pattern if regexp else re.escape(pattern)
        if backwards:
            found = None
            for m in re.finditer(pat, full[:start], flags):
                found = m
            if found is None:
                return ""
            m = found
        else:
            stop = len(full) if stopindex is None else min(self._pos(stopindex), len(full))
            m = re.compile(pat, flags).search(full, start, stop)
            if m is None and stopindex is None:
                m = re.compile(pat, flags).search(full, 0, start)
            if m is None:
                return ""
        if count is not None and isinstance(count, Variable):
            count.set(m.end() - m.start())
        return self.index(m.start())

    def edit_modified(self, arg=None):
        if arg is None:
            return getattr(self, "_modified", False)
        self._modified = _bool(arg)

    def edit_undo(self):
        pass

    def edit_redo(self):
        pass

    def edit_reset(self):
        pass

    def edit_separator(self):
        pass

    def edit(self, *args):
        return ""

    def xview(self, *args):
        return (0.0, 1.0)

    yview = xview_moveto = yview_moveto = xview_scroll = yview_scroll = xview

    def window_create(self, index, cnf={}, **kw):
        pass

    def image_create(self, index, cnf={}, **kw):
        pass

    def bbox(self, index):
        return None

    def dlineinfo(self, index):
        return None

    def count(self, index1, index2, *args):
        return (abs(self._pos(index2) - self._pos(index1)),)


class Listbox(Widget):
    _type = "listbox"
    _wclass = "Listbox"

    def _init(self):
        lv = self._opts.get("listvariable")
        self._items = list(self._split(lv.get())) if isinstance(lv, Variable) else []
        self._sel = []
        self._active = 0

    @staticmethod
    def _split(v):
        if isinstance(v, (tuple, list)):
            return [str(x) for x in v]
        v = str(v)
        if v.startswith("(") and v.endswith(")"):
            try:
                import ast
                return [str(x) for x in ast.literal_eval(v)]
            except Exception:
                pass
        return [a or b for a, b in re.findall(r"\{([^}]*)\}|(\S+)", v)]

    def _var_changed(self, var):
        if var is self._opts.get("listvariable"):
            self._items = list(self._split(var._value))
        self._send_props()

    def _props(self):
        p = self._style_props({})
        p["items"] = self._items
        p["selection"] = self._sel
        p["wchars"] = int(_px(self._opts.get("width"), 20)) or 20
        p["hlines"] = int(_px(self._opts.get("height"), 10)) or 10
        p["select_mode"] = str(self._opts.get("selectmode", "browse"))
        sbg = _g.color(self._opts.get("selectbackground"))
        if sbg:
            p["select_bg"] = sbg
        return p

    def _sync_var(self):
        lv = self._opts.get("listvariable")
        if isinstance(lv, Variable):
            lv._value = tuple(self._items)
            lv._fire("write")

    def _index(self, index):
        if index in (END, "end"):
            return len(self._items)
        if index in ("active", ACTIVE):
            return self._active
        if index in ("anchor",):
            return self._sel[0] if self._sel else 0
        if isinstance(index, str) and index.startswith("@"):
            return max(0, len(self._items) - 1)
        return int(index)

    def insert(self, index, *elements):
        i = self._index(index)
        for k, e in enumerate(elements):
            self._items.insert(i + k, str(e))
        self._sel = [s + len(elements) if s >= i else s for s in self._sel]
        self._sync_var()
        self._send_props()

    def delete(self, first, last=None):
        a = self._index(first)
        b = a if last is None else self._index(last)
        if last in (END, "end"):
            b = len(self._items) - 1
        del self._items[a:b + 1]
        n = b - a + 1
        self._sel = [s if s < a else s - n for s in self._sel if not (a <= s <= b)]
        self._sync_var()
        self._send_props()

    def get(self, first, last=None):
        a = self._index(first)
        if last is None:
            return self._items[a] if 0 <= a < len(self._items) else ""
        b = len(self._items) - 1 if last in (END, "end") else self._index(last)
        return tuple(self._items[a:b + 1])

    def size(self):
        return len(self._items)

    def curselection(self):
        return tuple(sorted(self._sel))

    def selection_set(self, first, last=None):
        a = self._index(first)
        b = a if last is None else (len(self._items) - 1 if last in (END, "end") else self._index(last))
        for i in range(a, b + 1):
            if i not in self._sel and 0 <= i < len(self._items):
                if str(self._opts.get("selectmode", "browse")) in ("browse", "single"):
                    self._sel = []
                self._sel.append(i)
        self._send_props()

    select_set = selection_set

    def selection_clear(self, first, last=None):
        a = self._index(first)
        b = a if last is None else (len(self._items) - 1 if last in (END, "end") else self._index(last))
        self._sel = [s for s in self._sel if not (a <= s <= b)]
        self._send_props()

    select_clear = selection_clear

    def selection_includes(self, index):
        return self._index(index) in self._sel

    select_includes = selection_includes

    def selection_anchor(self, index):
        pass

    select_anchor = selection_anchor

    def activate(self, index):
        self._active = self._index(index)

    def see(self, index):
        _g.bridge().send({"op": "set", "id": self._id, "props": {"see": self._index(index)}})

    def nearest(self, y):
        return max(0, min(len(self._items) - 1, int(y // 20)))

    def index(self, index):
        return self._index(index)

    def itemconfigure(self, index, cnf=None, **kw):
        pass

    itemconfig = itemconfigure

    def itemcget(self, index, option):
        return ""

    def bbox(self, index):
        return (0, int(self._index(index)) * 20, 100, 20)

    def xview(self, *args):
        return (0.0, 1.0)

    yview = xview_moveto = yview_moveto = xview_scroll = yview_scroll = xview

    def _on_value(self, value, ev):
        self._sel = [int(x) for x in (value or [])]
        if self._sel:
            self._active = self._sel[-1]
        _dispatch(self, "Virtual", "ListboxSelect", _g.Event(widget=self, type="Virtual"))
        if ev.get("double"):
            e = _g.Event(widget=self, type="ButtonPress", num=1)
            _dispatch(self, "ButtonPress", "1", e, frozenset({"Double"}))


class Scrollbar(Widget):
    """Android の一覧や文字欄は自分でスクロールするので、スクロールバーは場所を取らない。"""
    _type = "space"
    _wclass = "Scrollbar"

    def _props(self):
        return {"width": 0, "height": 0}

    def set(self, first, last):
        pass

    def get(self):
        return (0.0, 1.0)

    def activate(self, index=None):
        pass

    def delta(self, deltax, deltay):
        return 0.0

    def fraction(self, x, y):
        return 0.0

    def identify(self, x, y):
        return ""


class Scale(Widget):
    _type = "scale"
    _wclass = "Scale"
    _defaults = {"from": 0, "to": 100, "resolution": 1, "orient": "vertical", "showvalue": 1}

    def _init(self):
        if "from_" in self._opts:
            self._opts["from"] = self._opts.pop("from_")
        v = self._opts.get("variable")
        self._value = float(v.get()) if isinstance(v, Variable) else float(self._opts.get("from", 0))

    def _set_opt(self, key, value):
        if key == "from_":
            key = "from"
        Misc._set_opt(self, key, value)

    def _var_changed(self, var):
        try:
            self._value = float(var._value)
        except (TypeError, ValueError):
            pass
        self._send_props()

    def _props(self):
        p = self._style_props({})
        p["from"] = float(self._opts.get("from", 0))
        p["to"] = float(self._opts.get("to", 100))
        p["step"] = float(self._opts.get("resolution", 1) or 0)
        p["value"] = self._value
        p["orient"] = str(self._opts.get("orient", "vertical"))
        if self._opts.get("label"):
            p["label"] = str(self._opts["label"])
        p["showvalue"] = _bool(self._opts.get("showvalue", 1))
        ln = self._opts.get("length")
        if ln:
            p["width" if p["orient"].startswith("h") else "height"] = _px(ln)
        return p

    def _fmt(self):
        res = float(self._opts.get("resolution", 1) or 0)
        if res and float(res).is_integer():
            return str(int(round(self._value)))
        return str(self._value)

    def get(self):
        res = float(self._opts.get("resolution", 1) or 0)
        if res and float(res).is_integer():
            return int(round(self._value))
        return self._value

    def set(self, value):
        self._value = float(value)
        v = self._opts.get("variable")
        if isinstance(v, Variable):
            v._value = self.get()
            v._fire("write")
        self._send_props()

    def _on_value(self, value, ev):
        self._value = float(value)
        v = self._opts.get("variable")
        if isinstance(v, Variable):
            v._value = self.get()
            v._fire("write")
            for w in list(v._links):
                if w is not self:
                    w._var_changed(v)
        cmd = self._opts.get("command")
        if cmd:
            _g.bridge()._call(_resolve(cmd), self._fmt())

    def coords(self, value=None):
        return (0, 0)

    def identify(self, x, y):
        return ""


# ===================================================================== Canvas

class Canvas(Widget):
    _type = "canvas"
    _wclass = "Canvas"
    _defaults = {"width": 378, "height": 265}

    def _init(self):
        self._items = {}
        self._order = []
        self._next = itertools.count(1)
        self._tag_binds = {}
        self._current = None
        self._pressed_item = None
        self._windows = {}

    def _props(self):
        p = self._style_props({})
        p.pop("fg", None)
        p["width"] = _px(self._opts.get("width"), 378)
        p["height"] = _px(self._opts.get("height"), 265)
        p.setdefault("bg", "#ffffff")
        p["conf"] = True
        return p

    def _has_mouse_bindings(self):
        return bool(self._tag_binds) or Misc._has_mouse_bindings(self)

    def _dirty(self):
        _g.bridge().mark_dirty(("canvas", self._id), self._draw_msg)

    def _draw_msg(self):
        if self._destroyed:
            return None
        out = []
        for iid in list(self._order):
            it = self._items.get(iid)
            if it is None or it["type"] == "window":
                continue
            d = self._item_json(it)
            if d:
                out.append(d)
        return {"op": "set", "id": self._id, "props": {"draw": out}}

    def _item_json(self, it):
        o = it["opts"]
        if str(o.get("state", "")) == "hidden":
            return None
        t = it["type"]
        d = {"t": t, "c": [round(float(c), 2) for c in it["coords"]]}
        if t == "text":
            d["text"] = str(o.get("text", ""))
            d["fill"] = _g.color(o.get("fill"), "#000000") if o.get("fill", "black") != "" else None
            f = _g.font(o.get("font")) or {"family": "sans", "size": 13}
            d["font"] = f
            d["anchor"] = str(o.get("anchor", "center"))
            if o.get("width"):
                d["wrap"] = _px(o["width"])
            if o.get("justify"):
                d["justify"] = str(o["justify"])
            if o.get("angle"):
                d["angle"] = float(o["angle"])
            return d
        if t == "image":
            img = o.get("image")
            if isinstance(img, str):
                img = _images.get(img)
            if not isinstance(img, Image):
                return None
            d["img"] = img._ref()
            d["anchor"] = str(o.get("anchor", "center"))
            return d
        default_fill = "#000000" if t in ("line", "polygon") else None
        fill = o.get("fill", None)
        d["fill"] = _g.color(fill) if fill not in (None, "") else (default_fill if fill is None else None)
        if t == "line":
            d["outline"] = None
        else:
            outline = o.get("outline", None)
            default_outline = "#000000" if t in ("rectangle", "oval", "arc") else None
            d["outline"] = _g.color(outline) if outline not in (None, "") else (default_outline if outline is None else None)
        d["w"] = _px(o.get("width", 1), 1)
        if o.get("dash"):
            dash = o["dash"]
            d["dash"] = [float(x) for x in (dash if isinstance(dash, (tuple, list)) else [4, 4])]
        if t == "line":
            if o.get("arrow") and str(o["arrow"]) != "none":
                d["arrow"] = str(o["arrow"])
            if o.get("capstyle"):
                d["cap"] = str(o["capstyle"])
        if t in ("line", "polygon") and _bool(o.get("smooth", False)):
            d["smooth"] = True
        if t == "arc":
            d["start"] = float(o.get("start", 0))
            d["extent"] = float(o.get("extent", 90))
            d["style"] = str(o.get("style", "pieslice"))
        if o.get("stipple"):
            d["alpha"] = 0.5
        return d

    # ---- 作る

    def _create_item(self, itype, args, kw):
        coords = [float(c) for c in _flatten(args)]
        iid = next(self._next)
        tags = kw.pop("tags", kw.pop("tag", ()))
        if isinstance(tags, str):
            tags = tags.split()
        self._items[iid] = {"type": itype, "coords": coords, "opts": dict(kw), "tags": list(tags)}
        self._order.append(iid)
        if itype == "window":
            self._place_window(iid)
        self._dirty()
        return iid

    def create_line(self, *args, **kw):
        return self._create_item("line", args, kw)

    def create_rectangle(self, *args, **kw):
        return self._create_item("rectangle", args, kw)

    def create_oval(self, *args, **kw):
        return self._create_item("oval", args, kw)

    def create_polygon(self, *args, **kw):
        return self._create_item("polygon", args, kw)

    def create_arc(self, *args, **kw):
        return self._create_item("arc", args, kw)

    def create_text(self, *args, **kw):
        return self._create_item("text", args, kw)

    def create_image(self, *args, **kw):
        return self._create_item("image", args, kw)

    def create_bitmap(self, *args, **kw):
        return self._create_item("rectangle", args + (args[0] if args else 0,), {"outline": ""})

    def create_window(self, *args, **kw):
        return self._create_item("window", args, kw)

    def _place_window(self, iid):
        it = self._items[iid]
        w = it["opts"].get("window")
        if not isinstance(w, Misc) or not it["coords"]:
            return
        x, y = it["coords"][0], it["coords"][1]
        kw = {"x": x, "y": y, "anchor": it["opts"].get("anchor", "center")}
        if it["opts"].get("width"):
            kw["width"] = it["opts"]["width"]
        if it["opts"].get("height"):
            kw["height"] = it["opts"]["height"]
        w.place(**kw)

    # ---- 探す

    def _ids(self, tagOrId):
        if tagOrId is None:
            return []
        if isinstance(tagOrId, int) or (isinstance(tagOrId, str) and tagOrId.isdigit()):
            i = int(tagOrId)
            return [i] if i in self._items else []
        if isinstance(tagOrId, (tuple, list)):
            out = []
            for t in tagOrId:
                out += self._ids(t)
            return out
        tag = str(tagOrId)
        if tag == "all":
            return list(self._order)
        if tag == "current":
            return [self._current] if self._current in self._items else []
        return [i for i in self._order if tag in self._items[i]["tags"]]

    def _bbox_of(self, iid):
        it = self._items[iid]
        c = it["coords"]
        if not c:
            return None
        if it["type"] in ("text", "image", "window"):
            x, y = c[0], c[1]
            o = it["opts"]
            if it["type"] == "text":
                f = _g.font(o.get("font")) or {"size": 13}
                size = f.get("size", 13)
                lines = str(o.get("text", "")).split("\n")
                w = max(len(l) for l in lines) * size * 0.6 if lines else 0
                h = len(lines) * size * 1.3
            elif it["type"] == "image":
                img = o.get("image")
                img = _images.get(img) if isinstance(img, str) else img
                w, h = (img.width(), img.height()) if isinstance(img, Image) else (0, 0)
            else:
                w, h = _px(o.get("width"), 50), _px(o.get("height"), 20)
            a = str(o.get("anchor", "center"))
            x0 = x - w / 2 if "w" not in a and "e" not in a else (x if "w" in a else x - w)
            y0 = y - h / 2 if "n" not in a and "s" not in a else (y if "n" in a else y - h)
            return (x0, y0, x0 + w, y0 + h)
        xs, ys = c[0::2], c[1::2]
        pad = _px(it["opts"].get("width", 1), 1) / 2
        return (min(xs) - pad, min(ys) - pad, max(xs) + pad, max(ys) + pad)

    def _hit(self, iid, x, y):
        it = self._items[iid]
        if str(it["opts"].get("state", "")) in ("hidden", "disabled"):
            return False
        bb = self._bbox_of(iid)
        if bb is None:
            return False
        slop = 4
        if it["type"] == "line":
            c = it["coords"]
            r = _px(it["opts"].get("width", 1), 1) / 2 + slop
            for i in range(0, len(c) - 3, 2):
                if _seg_dist(x, y, c[i], c[i + 1], c[i + 2], c[i + 3]) <= r:
                    return True
            return False
        if it["type"] == "oval":
            cx, cy = (bb[0] + bb[2]) / 2, (bb[1] + bb[3]) / 2
            rx, ry = max(1, (bb[2] - bb[0]) / 2 + slop), max(1, (bb[3] - bb[1]) / 2 + slop)
            return ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1
        if it["type"] == "polygon":
            return _point_in_poly(x, y, it["coords"]) or \
                (bb[0] - slop <= x <= bb[2] + slop and bb[1] - slop <= y <= bb[3] + slop and False)
        return bb[0] - slop <= x <= bb[2] + slop and bb[1] - slop <= y <= bb[3] + slop

    def find_all(self):
        return tuple(self._order)

    def find_withtag(self, tagOrId):
        return tuple(self._ids(tagOrId))

    def find_overlapping(self, x1, y1, x2, y2):
        out = []
        for i in self._order:
            bb = self._bbox_of(i)
            if bb and bb[0] <= x2 and bb[2] >= x1 and bb[1] <= y2 and bb[3] >= y1:
                out.append(i)
        return tuple(out)

    def find_enclosed(self, x1, y1, x2, y2):
        out = []
        for i in self._order:
            bb = self._bbox_of(i)
            if bb and bb[0] >= x1 and bb[2] <= x2 and bb[1] >= y1 and bb[3] <= y2:
                out.append(i)
        return tuple(out)

    def find_closest(self, x, y, halo=None, start=None):
        for i in reversed(self._order):
            if self._hit(i, x, y):
                return (i,)
        best, bd = None, None
        for i in self._order:
            bb = self._bbox_of(i)
            if not bb:
                continue
            dx = max(bb[0] - x, 0, x - bb[2])
            dy = max(bb[1] - y, 0, y - bb[3])
            d = dx * dx + dy * dy
            if bd is None or d < bd:
                best, bd = i, d
        return (best,) if best is not None else ()

    def find_above(self, tagOrId):
        ids = self._ids(tagOrId)
        if not ids:
            return ()
        k = self._order.index(ids[-1])
        return (self._order[k + 1],) if k + 1 < len(self._order) else ()

    def find_below(self, tagOrId):
        ids = self._ids(tagOrId)
        if not ids:
            return ()
        k = self._order.index(ids[0])
        return (self._order[k - 1],) if k > 0 else ()

    def find(self, *args):
        kind = args[0]
        return getattr(self, "find_" + kind)(*args[1:])

    def bbox(self, *args):
        boxes = [self._bbox_of(i) for a in args for i in self._ids(a)]
        boxes = [b for b in boxes if b]
        if not boxes:
            return None
        return (int(min(b[0] for b in boxes)), int(min(b[1] for b in boxes)),
                int(max(b[2] for b in boxes)), int(max(b[3] for b in boxes)))

    # ---- 変える

    def coords(self, tagOrId, *args):
        ids = self._ids(tagOrId)
        if not ids:
            return []
        if not args:
            return [float(c) for c in self._items[ids[0]]["coords"]]
        coords = [float(c) for c in _flatten(args)]
        for i in ids[:1]:
            self._items[i]["coords"] = coords
            if self._items[i]["type"] == "window":
                self._place_window(i)
        self._dirty()

    def move(self, tagOrId, xAmount, yAmount):
        dx, dy = float(xAmount), float(yAmount)
        for i in self._ids(tagOrId):
            c = self._items[i]["coords"]
            self._items[i]["coords"] = [v + (dx if k % 2 == 0 else dy) for k, v in enumerate(c)]
            if self._items[i]["type"] == "window":
                self._place_window(i)
        self._dirty()

    def moveto(self, tagOrId, x="", y=""):
        bb = self.bbox(tagOrId)
        if not bb:
            return
        dx = float(x) - bb[0] if x != "" else 0
        dy = float(y) - bb[1] if y != "" else 0
        self.move(tagOrId, dx, dy)

    def scale(self, tagOrId, xOrigin, yOrigin, xScale, yScale):
        x0, y0, sx, sy = float(xOrigin), float(yOrigin), float(xScale), float(yScale)
        for i in self._ids(tagOrId):
            c = self._items[i]["coords"]
            self._items[i]["coords"] = [(x0 + (v - x0) * sx) if k % 2 == 0 else (y0 + (v - y0) * sy)
                                        for k, v in enumerate(c)]
        self._dirty()

    def itemconfigure(self, tagOrId, cnf=None, **kw):
        kw = _cnfmerge((cnf, kw))
        ids = self._ids(tagOrId)
        if not kw:
            return {k: (k, "", "", "", v) for k, v in self._items[ids[0]]["opts"].items()} if ids else {}
        for i in ids:
            tags = kw.pop("tags", None)
            if tags is not None:
                self._items[i]["tags"] = tags.split() if isinstance(tags, str) else list(tags)
            self._items[i]["opts"].update(kw)
            if self._items[i]["type"] == "window":
                self._place_window(i)
        self._dirty()

    itemconfig = itemconfigure

    def itemcget(self, tagOrId, option):
        ids = self._ids(tagOrId)
        if not ids:
            return ""
        if option == "tags":
            return " ".join(self._items[ids[0]]["tags"])
        return self._items[ids[0]]["opts"].get(option, "")

    def type(self, tagOrId):
        ids = self._ids(tagOrId)
        return self._items[ids[0]]["type"] if ids else None

    def delete(self, *args):
        for a in args:
            for i in self._ids(a):
                it = self._items.pop(i, None)
                if it and it["type"] == "window" and isinstance(it["opts"].get("window"), Misc):
                    it["opts"]["window"].place_forget()
                if i in self._order:
                    self._order.remove(i)
        self._dirty()

    def gettags(self, tagOrId):
        ids = self._ids(tagOrId)
        return tuple(self._items[ids[0]]["tags"]) if ids else ()

    def addtag_withtag(self, newtag, tagOrId):
        for i in self._ids(tagOrId):
            if newtag not in self._items[i]["tags"]:
                self._items[i]["tags"].append(newtag)

    def addtag_all(self, newtag):
        self.addtag_withtag(newtag, "all")

    def addtag_closest(self, newtag, x, y, halo=None, start=None):
        for i in self.find_closest(x, y):
            self.addtag_withtag(newtag, i)

    def addtag_overlapping(self, newtag, x1, y1, x2, y2):
        for i in self.find_overlapping(x1, y1, x2, y2):
            self.addtag_withtag(newtag, i)

    def addtag_enclosed(self, newtag, x1, y1, x2, y2):
        for i in self.find_enclosed(x1, y1, x2, y2):
            self.addtag_withtag(newtag, i)

    def addtag_above(self, newtag, tagOrId):
        for i in self.find_above(tagOrId):
            self.addtag_withtag(newtag, i)

    def addtag_below(self, newtag, tagOrId):
        for i in self.find_below(tagOrId):
            self.addtag_withtag(newtag, i)

    def addtag(self, *args):
        pass

    def dtag(self, tagOrId, tagToDelete=None):
        tag = tagToDelete if tagToDelete is not None else tagOrId
        for i in self._ids(tagOrId):
            if tag in self._items[i]["tags"]:
                self._items[i]["tags"].remove(tag)

    def tag_raise(self, tagOrId, aboveThis=None):
        ids = self._ids(tagOrId)
        for i in ids:
            self._order.remove(i)
        if aboveThis is not None:
            above = self._ids(aboveThis)
            k = self._order.index(above[-1]) + 1 if above else len(self._order)
            self._order[k:k] = ids
        else:
            self._order.extend(ids)
        self._dirty()

    lift = tkraise = tag_raise

    def tag_lower(self, tagOrId, belowThis=None):
        ids = self._ids(tagOrId)
        for i in ids:
            self._order.remove(i)
        if belowThis is not None:
            below = self._ids(belowThis)
            k = self._order.index(below[0]) if below else 0
            self._order[k:k] = ids
        else:
            self._order[0:0] = ids
        self._dirty()

    lower = tag_lower

    def tag_bind(self, tagOrId, sequence=None, func=None, add=None):
        if func is None:
            return ""
        table = self._tag_binds.setdefault(str(tagOrId), {})
        fid = _add_binding(table, sequence, func, add)
        self._update_mouse_flag()
        return fid

    def tag_unbind(self, tagOrId, sequence, funcid=None):
        self._tag_binds.get(str(tagOrId), {}).pop(_parse_seq(sequence), None)

    def canvasx(self, screenx, gridspacing=None):
        return float(screenx)

    def canvasy(self, screeny, gridspacing=None):
        return float(screeny)

    def focus(self, *args):
        return self.focus_set()

    def icursor(self, *args):
        pass

    def index(self, tagOrId, index):
        return 0

    def insert(self, tagOrId, beforeThis, string):
        for i in self._ids(tagOrId):
            o = self._items[i]["opts"]
            t = str(o.get("text", ""))
            k = len(t) if beforeThis in ("end", END) else int(beforeThis)
            o["text"] = t[:k] + str(string) + t[k:]
        self._dirty()

    def dchars(self, tagOrId, first=0, last=None):
        for i in self._ids(tagOrId):
            o = self._items[i]["opts"]
            t = str(o.get("text", ""))
            a = int(first)
            b = len(t) if last in (None, "end", END) else int(last) + 1
            o["text"] = t[:a] + t[b:]
        self._dirty()

    def select_clear(self):
        pass

    def postscript(self, cnf={}, **kw):
        return ""

    def xview(self, *args):
        return (0.0, 1.0)

    yview = xview_moveto = yview_moveto = xview_scroll = yview_scroll = xview

    def scan_mark(self, x, y):
        pass

    def scan_dragto(self, x, y, gain=10):
        pass

    def _on_mouse(self, ev):
        t = ev.get("type")
        etype = {"press": "ButtonPress", "double": "ButtonPress", "release": "ButtonRelease",
                 "motion": "Motion", "wheel": "MouseWheel"}.get(t)
        if etype is None:
            return
        e, mods = self._mouse_event(ev, etype)
        x, y = float(ev.get("x", 0)), float(ev.get("y", 0))
        if etype == "ButtonPress" or self._pressed_item is None:
            hit = None
            for i in reversed(self._order):
                if self._hit(i, x, y):
                    hit = i
                    break
            self._current = hit
            if etype == "ButtonPress":
                self._pressed_item = hit
        item = self._pressed_item if etype in ("Motion", "ButtonRelease") and self._pressed_item else self._current
        detail = str(ev.get("btn", 1)) if etype in ("ButtonPress", "ButtonRelease") else None
        if item is not None and item in self._items:
            for tag in [str(item)] + self._items[item]["tags"] + ["all"]:
                table = self._tag_binds.get(tag)
                if not table:
                    continue
                for _, fn in list(_match(table, etype, detail, mods)):
                    r = _g.bridge()._call(fn, e)
                    if r == "break":
                        return
        if etype == "ButtonRelease":
            self._pressed_item = None
        _dispatch(self, etype, detail, e, mods)


def _seg_dist(px, py, x1, y1, x2, y2):
    dx, dy = x2 - x1, y2 - y1
    if dx == dy == 0:
        return ((px - x1) ** 2 + (py - y1) ** 2) ** 0.5
    t = max(0, min(1, ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy)))
    qx, qy = x1 + t * dx, y1 + t * dy
    return ((px - qx) ** 2 + (py - qy) ** 2) ** 0.5


def _point_in_poly(x, y, c):
    pts = list(zip(c[0::2], c[1::2]))
    inside = False
    n = len(pts)
    for i in range(n):
        x1, y1 = pts[i]
        x2, y2 = pts[(i + 1) % n]
        if (y1 > y) != (y2 > y) and x < (x2 - x1) * (y - y1) / ((y2 - y1) or 1e-9) + x1:
            inside = not inside
    return inside


# ===================================================================== メニュー

_menus = {}


class Menu(Widget):
    _type = "space"
    _wclass = "Menu"

    def _init(self):
        self._entries = []
        self._owners = set()
        self._parents = set()
        _menus[self._id] = self

    def _create(self, parent_id):
        pass  # メニューは窓の見出しの「︙」やポップアップとして出すので、部品は作らない

    def _props(self):
        return {}

    def _send_props(self):
        self._changed()

    def _changed(self):
        for w in list(self._owners):
            if not w._destroyed:
                _g.bridge().send({"op": "set", "id": w._id, "props": {"menu": self._json()}})
        for p in list(self._parents):
            p._changed()

    def _json(self):
        out = []
        for i, e in enumerate(self._entries):
            t = e["type"]
            if t == "separator":
                out.append({"type": "separator"})
                continue
            if t == "tearoff":
                continue
            d = {"type": t, "label": str(e.get("label", "")), "key": "%d:%d" % (self._id, i),
                 "enabled": str(e.get("state", "normal")) != "disabled"}
            if e.get("accelerator"):
                d["accel"] = str(e["accelerator"])
            if t == "cascade" and isinstance(e.get("menu"), Menu):
                d["items"] = e["menu"]._json()
            if t == "checkbutton":
                v = e.get("variable")
                d["checked"] = isinstance(v, Variable) and str(v._value) == str(e.get("onvalue", 1))
            if t == "radiobutton":
                v = e.get("variable")
                d["checked"] = isinstance(v, Variable) and str(v._value) == str(e.get("value", e.get("label")))
            out.append(d)
        return out

    def _add(self, itemType, cnf, kw, index=None):
        e = _cnfmerge((cnf, kw))
        e["type"] = itemType
        if itemType == "checkbutton" and e.get("variable") is None:
            e["variable"] = IntVar(value=0)
        if itemType == "radiobutton" and e.get("variable") is None:
            e["variable"] = _shared_radio_var()
        for key in ("variable",):
            if isinstance(e.get(key), Variable):
                e[key]._link(self)
        if isinstance(e.get("menu"), Menu):
            e["menu"]._parents.add(self)
        if index is None:
            self._entries.append(e)
        else:
            self._entries.insert(self.index(index), e)
        self._changed()

    def _var_changed(self, var):
        self._changed()

    def add(self, itemType, cnf={}, **kw):
        self._add(itemType, cnf, kw)

    def add_cascade(self, cnf={}, **kw):
        self._add("cascade", cnf, kw)

    def add_checkbutton(self, cnf={}, **kw):
        self._add("checkbutton", cnf, kw)

    def add_command(self, cnf={}, **kw):
        self._add("command", cnf, kw)

    def add_radiobutton(self, cnf={}, **kw):
        self._add("radiobutton", cnf, kw)

    def add_separator(self, cnf={}, **kw):
        self._add("separator", cnf, kw)

    def insert(self, index, itemType, cnf={}, **kw):
        self._add(itemType, cnf, kw, index)

    def insert_cascade(self, index, cnf={}, **kw):
        self._add("cascade", cnf, kw, index)

    def insert_checkbutton(self, index, cnf={}, **kw):
        self._add("checkbutton", cnf, kw, index)

    def insert_command(self, index, cnf={}, **kw):
        self._add("command", cnf, kw, index)

    def insert_radiobutton(self, index, cnf={}, **kw):
        self._add("radiobutton", cnf, kw, index)

    def insert_separator(self, index, cnf={}, **kw):
        self._add("separator", cnf, kw, index)

    def index(self, index):
        if index in (END, "end", "last"):
            return len(self._entries) - 1 if self._entries else None
        if isinstance(index, int):
            return index
        if isinstance(index, str) and index.isdigit():
            return int(index)
        for i, e in enumerate(self._entries):
            if str(e.get("label", "")) == str(index):
                return i
        return None

    def delete(self, index1, index2=None):
        a = self.index(index1)
        if a is None:
            return
        b = a if index2 is None else self.index(index2)
        del self._entries[a:b + 1]
        self._changed()

    def entryconfigure(self, index, cnf=None, **kw):
        i = self.index(index)
        if i is None:
            return None
        kw = _cnfmerge((cnf, kw))
        if not kw:
            return dict(self._entries[i])
        self._entries[i].update(kw)
        self._changed()

    entryconfig = entryconfigure

    def entrycget(self, index, option):
        i = self.index(index)
        return self._entries[i].get(option, "") if i is not None else ""

    def type(self, index):
        i = self.index(index)
        return self._entries[i]["type"] if i is not None else None

    def invoke(self, index):
        i = self.index(index)
        if i is None or i >= len(self._entries):
            return None
        e = self._entries[i]
        if str(e.get("state", "normal")) == "disabled":
            return None
        v = e.get("variable")
        if e["type"] == "checkbutton" and isinstance(v, Variable):
            on = str(v._value) == str(e.get("onvalue", 1))
            v.set(e.get("offvalue", 0) if on else e.get("onvalue", 1))
        elif e["type"] == "radiobutton" and isinstance(v, Variable):
            v.set(e.get("value", e.get("label")))
        cmd = e.get("command")
        if cmd:
            return _g.bridge()._call(_resolve(cmd))
        return None

    def post(self, x, y):
        key = _g.bridge().request({"op": "popup", "items": self._json(), "x": x, "y": y})
        if key:
            mid, _, idx = str(key).partition(":")
            m = _menus.get(int(mid)) if mid.isdigit() else None
            if m is not None and idx.isdigit():
                m.invoke(int(idx))

    def tk_popup(self, x, y, entry=""):
        self.post(x, y)

    def unpost(self):
        pass

    def activate(self, index):
        pass

    def yposition(self, index):
        return 0

    def xposition(self, index):
        return 0

    def destroy(self):
        _menus.pop(self._id, None)
        Misc._destroy_quiet(self)
        self._detach()


class Menubutton(Button):
    _type = "button"
    _wclass = "Menubutton"

    def _props(self):
        p = Button._props(self)
        p["style"] = "outlined"
        p["dropdown"] = True
        return p

    def invoke(self):
        m = self._opts.get("menu")
        if isinstance(m, Menu):
            m.post(0, 0)


class OptionMenu(Widget):
    _type = "combo"
    _wclass = "Menubutton"

    def __init__(self, master, variable, value, *values, **kwargs):
        self._variable = variable
        self._values = [value] + list(values)
        self._command = kwargs.pop("command", None)
        Widget.__init__(self, master, textvariable=variable)
        self._menu = Menu(self, tearoff=0)
        for v in self._values:
            self._menu.add_command(label=v, command=lambda v=v: self._pick(v))
        self.widgetName = "tk_optionMenu"

    def _props(self):
        p = self._style_props({})
        p["items"] = [str(v) for v in self._values]
        p["value"] = str(self._variable.get()) if isinstance(self._variable, Variable) else ""
        p["readonly"] = True
        return p

    def _pick(self, v):
        if isinstance(self._variable, Variable):
            self._variable.set(v)
        if self._command:
            _g.bridge()._call(self._command, v)

    def _on_value(self, value, ev):
        self._pick(value)

    def __getitem__(self, name):
        if name == "menu":
            return self._menu
        return Widget.__getitem__(self, name)


class PanedWindow(Widget):
    _type = "frame"
    _wclass = "Panedwindow"
    _container = True
    _defaults = {"orient": "horizontal"}

    def _props(self):
        p = Frame._props(self)
        p["orient"] = "h" if str(self._opts.get("orient", "horizontal")).startswith("h") else "v"
        return p

    def add(self, child, **kw):
        if child not in self._pack_list:
            self._pack_list.append(child)
        weight = float(kw.get("weight", kw.get("stretch") == "always" and 1 or 1))
        _g.bridge().send({"op": "layout", "id": child._id,
                          "opts": {"m": "box", "weight": weight, "expand": True}})
        _g.bridge().send({"op": "order", "id": self._id, "ids": [w._id for w in self._pack_list]})

    def remove(self, child):
        if child in self._pack_list:
            self._pack_list.remove(child)
        _g.bridge().send({"op": "layout", "id": child._id, "opts": {"m": "none"}})

    forget = remove

    def panes(self):
        return tuple(self._pack_list)

    def paneconfigure(self, tagOrId, cnf=None, **kw):
        pass

    paneconfig = paneconfigure

    def sash_coord(self, index):
        return (0, 0)

    def sash_place(self, index, x, y):
        pass

    def sashpos(self, index, newpos=None):
        return 0


# ===================================================================== その他の名前

class EventType:
    KeyPress = "2"
    KeyRelease = "3"
    ButtonPress = "4"
    ButtonRelease = "5"
    Motion = "6"
    Enter = "7"
    Leave = "8"
    FocusIn = "9"
    FocusOut = "10"
    Configure = "22"
    MouseWheel = "38"


Event = _g.Event
Studbutton = Button
Tributton = Button


class CallWrapper:
    def __init__(self, func, subst, widget):
        self.func = func

    def __call__(self, *args):
        return self.func(*args)


def _test():
    root = Tk()
    Label(root, text="This is Tcl/Tk version %s" % TclVersion).pack()
    Button(root, text="Click me!", command=lambda: root.destroy()).pack()
    root.mainloop()


if __name__ == "__main__":
    _test()
