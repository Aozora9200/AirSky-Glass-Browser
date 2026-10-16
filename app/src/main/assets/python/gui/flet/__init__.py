"""AirSky Browser の Flet 互換モジュール。

Flet の Page とコントロール(Text・TextField・各種ボタン・Row・Column・Container・ListView など)を、
Flutter の代わりに Android の部品(Pixel 風)で表示する。ft.app(target=main) / ft.run(main) で始める。
async の main やイベント処理にも対応している。
"""

import asyncio
import inspect
import itertools
import json
import os
import threading
import time

import _airsky_gui as _g

__version__ = "0.28.3"
version = __version__

_lock = threading.RLock()


# ===================================================================== 値の型

class _Enum(str):
    pass


class _NS:
    """ft.Colors.RED → "red" のように、属性名をそのまま値にする名前空間。"""

    def __init__(self, lower=True, mapping=None):
        self._lower = lower
        self._map = mapping or {}

    def __getattr__(self, name):
        if name.startswith("__"):
            raise AttributeError(name)
        if name in self._map:
            return self._map[name]
        return name.lower() if self._lower else name

    def __call__(self, *a, **kw):
        return a[0] if a else None


class _ColorsNS(_NS):
    @staticmethod
    def with_opacity(opacity, color):
        c = _g.color(color) or "#000000"
        return "#%02x%s" % (max(0, min(255, round(float(opacity) * 255))), c[-6:])

    def __getattr__(self, name):
        if name.startswith("__"):
            raise AttributeError(name)
        return name.lower()


Colors = colors = _ColorsNS()
CupertinoColors = cupertino_colors = _ColorsNS()
Icons = icons = _NS()
CupertinoIcons = cupertino_icons = _NS()


class MainAxisAlignment:
    START = "start"
    END = "end"
    CENTER = "center"
    SPACE_BETWEEN = "spaceBetween"
    SPACE_AROUND = "spaceAround"
    SPACE_EVENLY = "spaceEvenly"


class CrossAxisAlignment:
    START = "start"
    END = "end"
    CENTER = "center"
    STRETCH = "stretch"
    BASELINE = "baseline"


class TextAlign:
    LEFT = "left"
    RIGHT = "right"
    CENTER = "center"
    JUSTIFY = "justify"
    START = "start"
    END = "end"


class FontWeight:
    NORMAL = "normal"
    BOLD = "bold"
    W_100 = "w100"
    W_200 = "w200"
    W_300 = "w300"
    W_400 = "w400"
    W_500 = "w500"
    W_600 = "w600"
    W_700 = "w700"
    W_800 = "w800"
    W_900 = "w900"


class TextOverflow:
    CLIP = "clip"
    ELLIPSIS = "ellipsis"
    FADE = "fade"
    VISIBLE = "visible"


class KeyboardType:
    NONE = "none"
    TEXT = "text"
    MULTILINE = "multiline"
    NUMBER = "number"
    PHONE = "phone"
    DATETIME = "datetime"
    EMAIL = "email"
    URL = "url"
    VISIBLE_PASSWORD = "visiblePassword"
    NAME = "name"
    STREET_ADDRESS = "streetAddress"


class ThemeMode:
    SYSTEM = "system"
    LIGHT = "light"
    DARK = "dark"


class ImageFit:
    NONE = "none"
    CONTAIN = "contain"
    COVER = "cover"
    FILL = "fill"
    FIT_HEIGHT = "fitHeight"
    FIT_WIDTH = "fitWidth"
    SCALE_DOWN = "scaleDown"


class ScrollMode:
    AUTO = "auto"
    ADAPTIVE = "adaptive"
    ALWAYS = "always"
    HIDDEN = "hidden"


class ClipBehavior:
    NONE = "none"
    HARD_EDGE = "hardEdge"
    ANTI_ALIAS = "antiAlias"
    ANTI_ALIAS_WITH_SAVE_LAYER = "antiAliasWithSaveLayer"


class AppView:
    FLET_APP = "flet_app"
    WEB_BROWSER = "web_browser"
    FLET_APP_HIDDEN = "flet_app_hidden"
    FLET_APP_WEB = "flet_app_web"


class WebRenderer:
    AUTO = "auto"
    CANVAS_KIT = "canvaskit"
    HTML = "html"


class BorderSide:
    def __init__(self, width=1, color=None, **kw):
        self.width, self.color = width, color


class BorderStyle:
    NONE = "none"
    SOLID = "solid"


class MouseCursor(_NS):
    pass


class LabelPosition:
    LEFT = "left"
    RIGHT = "right"


class NavigationBarLabelBehavior:
    ALWAYS_SHOW = "alwaysShow"


class Padding:
    def __init__(self, left=0, top=0, right=0, bottom=0):
        self.left, self.top, self.right, self.bottom = left, top, right, bottom

    def _list(self):
        return [self.left or 0, self.top or 0, self.right or 0, self.bottom or 0]


class _PaddingNS:
    @staticmethod
    def all(value):
        return Padding(value, value, value, value)

    @staticmethod
    def symmetric(vertical=0, horizontal=0):
        return Padding(horizontal, vertical, horizontal, vertical)

    @staticmethod
    def only(left=0, top=0, right=0, bottom=0):
        return Padding(left, top, right, bottom)

    def __call__(self, *a, **kw):
        return Padding(*a, **kw)

    Padding = Padding


padding = _PaddingNS()
margin = _PaddingNS()
Margin = Padding


def _pad(v):
    if v is None:
        return None
    if isinstance(v, (int, float)):
        return [v, v, v, v]
    if isinstance(v, Padding):
        return v._list()
    if isinstance(v, (tuple, list)):
        return list(v) + [0] * (4 - len(v))
    return None


class BorderRadius:
    def __init__(self, top_left=0, top_right=0, bottom_left=0, bottom_right=0):
        self.top_left, self.top_right, self.bottom_left, self.bottom_right = top_left, top_right, bottom_left, bottom_right

    def _value(self):
        return max(self.top_left, self.top_right, self.bottom_left, self.bottom_right)


class _BorderRadiusNS:
    @staticmethod
    def all(value):
        return BorderRadius(value, value, value, value)

    @staticmethod
    def only(top_left=0, top_right=0, bottom_left=0, bottom_right=0):
        return BorderRadius(top_left, top_right, bottom_left, bottom_right)

    @staticmethod
    def vertical(top=0, bottom=0):
        return BorderRadius(top, top, bottom, bottom)

    @staticmethod
    def horizontal(left=0, right=0):
        return BorderRadius(left, right, left, right)

    BorderRadius = BorderRadius


border_radius = _BorderRadiusNS()


def _radius(v):
    if v is None:
        return None
    if isinstance(v, (int, float)):
        return v
    if isinstance(v, BorderRadius):
        return v._value()
    return None


class Border:
    def __init__(self, top=None, right=None, bottom=None, left=None):
        self.top, self.right, self.bottom, self.left = top, right, bottom, left


class _BorderNS:
    @staticmethod
    def all(width=1, color=None):
        s = BorderSide(width, color)
        return Border(s, s, s, s)

    @staticmethod
    def only(left=None, top=None, right=None, bottom=None):
        return Border(top, right, bottom, left)

    @staticmethod
    def symmetric(vertical=None, horizontal=None):
        return Border(vertical, horizontal, vertical, horizontal)

    Border = Border
    BorderSide = BorderSide


border = _BorderNS()


class InputBorder:
    NONE = "none"
    OUTLINE = "outline"
    UNDERLINE = "underline"


class Alignment:
    def __init__(self, x=0, y=0):
        self.x, self.y = x, y


class _AlignmentNS:
    center = Alignment(0, 0)
    top_left = Alignment(-1, -1)
    top_center = Alignment(0, -1)
    top_right = Alignment(1, -1)
    center_left = Alignment(-1, 0)
    center_right = Alignment(1, 0)
    bottom_left = Alignment(-1, 1)
    bottom_center = Alignment(0, 1)
    bottom_right = Alignment(1, 1)
    CENTER = center
    TOP_LEFT = top_left
    TOP_CENTER = top_center
    TOP_RIGHT = top_right
    CENTER_LEFT = center_left
    CENTER_RIGHT = center_right
    BOTTOM_LEFT = bottom_left
    BOTTOM_CENTER = bottom_center
    BOTTOM_RIGHT = bottom_right
    Alignment = Alignment


alignment = _AlignmentNS()


class TextStyle:
    def __init__(self, size=None, weight=None, italic=None, color=None, font_family=None,
                 bgcolor=None, decoration=None, **kw):
        self.size, self.weight, self.italic, self.color = size, weight, italic, color
        self.font_family, self.bgcolor, self.decoration = font_family, bgcolor, decoration


class TextDecoration:
    NONE = 0
    UNDERLINE = 1
    OVERLINE = 2
    LINE_THROUGH = 4


class ButtonStyle:
    def __init__(self, color=None, bgcolor=None, padding=None, shape=None, elevation=None, **kw):
        self.color, self.bgcolor, self.padding, self.shape, self.elevation = color, bgcolor, padding, shape, elevation


class RoundedRectangleBorder:
    def __init__(self, radius=0, **kw):
        self.radius = radius


class StadiumBorder(RoundedRectangleBorder):
    pass


class CircleBorder(RoundedRectangleBorder):
    pass


class BeveledRectangleBorder(RoundedRectangleBorder):
    pass


class ContinuousRectangleBorder(RoundedRectangleBorder):
    pass


class BoxShadow:
    def __init__(self, *a, **kw):
        pass


class LinearGradient:
    def __init__(self, begin=None, end=None, colors=None, **kw):
        self.colors = colors or []


RadialGradient = SweepGradient = LinearGradient


class Theme:
    def __init__(self, color_scheme_seed=None, color_scheme=None, font_family=None, use_material3=True, **kw):
        self.color_scheme_seed = color_scheme_seed
        self.color_scheme = color_scheme
        self.font_family = font_family


class ColorScheme:
    def __init__(self, primary=None, **kw):
        self.primary = primary


class VisualDensity:
    COMFORTABLE = "comfortable"
    COMPACT = "compact"
    STANDARD = "standard"


class Animation:
    def __init__(self, duration=0, curve=None):
        self.duration, self.curve = duration, curve


class AnimationCurve(_NS):
    pass


AnimationCurve = _NS()


class Offset:
    def __init__(self, x=0, y=0):
        self.x, self.y = x, y


class Scale:
    def __init__(self, scale=1, **kw):
        self.scale = scale


class Rotate:
    def __init__(self, angle=0, **kw):
        self.angle = angle


# ===================================================================== イベント

class ControlEvent:
    def __init__(self, target=None, name="", data=None, control=None, page=None):
        self.target = target
        self.name = name
        self.data = data
        self.control = control
        self.page = page


Event = ControlEvent


class KeyboardEvent(ControlEvent):
    def __init__(self, key="", shift=False, ctrl=False, alt=False, meta=False, page=None):
        ControlEvent.__init__(self, page=page, name="keyboard_event")
        self.key, self.shift, self.ctrl, self.alt, self.meta = key, shift, ctrl, alt, meta


class TapEvent(ControlEvent):
    def __init__(self, local_x=0, local_y=0, control=None, page=None):
        ControlEvent.__init__(self, control=control, page=page, name="tap")
        self.local_x, self.local_y = local_x, local_y
        self.global_x, self.global_y = local_x, local_y


class DragUpdateEvent(TapEvent):
    def __init__(self, local_x=0, local_y=0, delta_x=0, delta_y=0, control=None, page=None):
        TapEvent.__init__(self, local_x, local_y, control, page)
        self.delta_x, self.delta_y = delta_x, delta_y


DragStartEvent = DragEndEvent = HoverEvent = TapEvent


class WindowResizeEvent(ControlEvent):
    def __init__(self, width=0, height=0, page=None):
        ControlEvent.__init__(self, page=page, name="resize")
        self.width, self.height = width, height


_loop = None
_loop_thread = None


def _async_loop():
    global _loop, _loop_thread
    if _loop is None:
        _loop = asyncio.new_event_loop()
        _loop_thread = threading.Thread(target=_loop.run_forever, name="flet-async", daemon=True)
        _loop_thread.start()
    return _loop


def _call_handler(handler, event, page):
    if handler is None:
        return
    try:
        nargs = len(inspect.signature(handler).parameters)
    except (TypeError, ValueError):
        nargs = 1
    args = (event,) if nargs else ()
    if inspect.iscoroutinefunction(handler):
        async def run():
            try:
                await handler(*args)
            except Exception:
                import traceback
                traceback.print_exc()
            if page is not None:
                page.update()
        asyncio.run_coroutine_threadsafe(run(), _async_loop())
        return
    with _lock:
        _g.bridge()._call(handler, *args)
        if page is not None:
            page.update()


# ===================================================================== コントロールの土台

_ids = itertools.count(1)


class Control:
    _type = "frame"
    _fill_cross = False  # Column の中で横いっぱいに広げる

    def __init__(self, ref=None, key=None, width=None, height=None, expand=None, expand_loose=None,
                 col=None, opacity=None, tooltip=None, visible=True, disabled=False, data=None,
                 left=None, top=None, right=None, bottom=None, rtl=False, adaptive=None, **kw):
        self.__dict__["_id"] = None
        self.__dict__["_sent"] = {}
        self.__dict__["_kids"] = []
        self.__dict__["_layout_sent"] = None
        self.__dict__["_parent"] = None
        self.__dict__["page"] = None
        self.uid = "_%d" % next(_ids)
        self.key = key
        self.width = width
        self.height = height
        self.expand = expand
        self.col = col
        self.opacity = opacity
        self.tooltip = tooltip
        self.visible = visible
        self.disabled = disabled
        self.data = data
        self.left, self.top, self.right, self.bottom = left, top, right, bottom
        for k, v in kw.items():
            setattr(self, k, v)
        if ref is not None:
            ref.current = self

    def __setattr__(self, name, value):
        self.__dict__[name] = value

    def __getattr__(self, name):
        if name.startswith("__"):
            raise AttributeError(name)
        return None

    # ---- 中身(サブクラスで決める)

    def _props(self):
        p = {}
        if self.width is not None:
            p["width"] = self.width
        if self.height is not None:
            p["height"] = self.height
        if self.visible is False:
            p["visible"] = False
        if self.disabled:
            p["state"] = "disabled"
        if self.opacity is not None:
            p["alpha"] = self.opacity
        if self.tooltip:
            p["tooltip"] = str(self.tooltip)
        return p

    def _children(self):
        return []

    def _child_opts(self, child, index):
        return None

    def _container_id(self):
        return self._id

    # ---- Android へ

    def _mount(self, parent_id, page):
        b = _g.bridge()
        self.__dict__["page"] = page
        if self._id is None:
            self.__dict__["_id"] = b.new_id()
            props = self._props()
            self.__dict__["_sent"] = dict(props)
            msg = {"op": "new", "id": self._id, "type": self._type, "props": props}
            if parent_id is not None:
                msg["parent"] = parent_id
            b.send(msg)
            b.handlers[self._id] = self._on_android
        self._sync_children(page)

    def _unmount(self):
        if self._id is None:
            return
        b = _g.bridge()
        b.handlers.pop(self._id, None)
        b.send({"op": "destroy", "id": self._id})
        self._forget()

    def _forget(self):
        _g.bridge().handlers.pop(self._id, None) if self._id is not None else None
        self.__dict__["_id"] = None
        self.__dict__["_sent"] = {}
        self.__dict__["_layout_sent"] = None
        for k in self._kids:
            k._forget()
        self.__dict__["_kids"] = []

    def _sync_children(self, page):
        b = _g.bridge()
        want = [c for c in self._children() if isinstance(c, Control)]
        old = self._kids
        for c in old:
            if c not in want and c._id is not None and c._parent is self:
                c._unmount()
        for i, c in enumerate(want):
            if c._id is not None and c._parent is not self:
                b.send({"op": "reparent", "id": c._id, "parent": self._container_id()})
            c.__dict__["_parent"] = self
            c._mount(self._container_id(), page)
            opts = self._child_opts(c, i)
            if opts is not None and opts != c._layout_sent:
                c.__dict__["_layout_sent"] = opts
                b.send({"op": "layout", "id": c._id, "opts": opts})
        ids = [c._id for c in want]
        if ids != [c._id for c in old] or len(old) != len(want):
            b.send({"op": "order", "id": self._container_id(), "ids": ids})
        self.__dict__["_kids"] = want

    def _refresh(self, page):
        if self._id is None:
            return
        props = self._props()
        diff = {k: v for k, v in props.items() if self._sent.get(k, _MISSING) != v}
        for k in self._sent:
            if k not in props:
                diff[k] = None
        if diff:
            self.__dict__["_sent"] = dict(props)
            _g.bridge().send({"op": "set", "id": self._id, "props": diff})
        self._sync_children(page)
        for c in self._kids:
            c._refresh(page)

    def update(self):
        with _lock:
            if self._id is not None:
                self._refresh(self.page)
            _g.bridge().flush()

    async def update_async(self):
        self.update()

    def clean(self):
        if hasattr(self, "controls") and isinstance(self.controls, list):
            self.controls.clear()
        self.update()

    def focus(self):
        if self._id is not None:
            _g.bridge().send({"op": "focus", "id": self._id})

    async def focus_async(self):
        self.focus()

    def _on_android(self, ev):
        pass

    def _fire(self, handler, data=None, name=""):
        e = ControlEvent(target=self.uid, name=name, data=data, control=self, page=self.page)
        _call_handler(handler, e, self.page)

    def _expand_weight(self):
        if self.expand is True:
            return 1.0
        if isinstance(self.expand, (int, float)) and not isinstance(self.expand, bool):
            return float(self.expand)
        return 0.0


_MISSING = object()


class Ref:
    def __init__(self):
        self.current = None


def _text_props(p, value, size=None, color=None, weight=None, italic=None, font_family=None,
                style=None, bgcolor=None, align=None):
    p["text"] = "" if value is None else str(value)
    f = {"family": "sans"}
    if style is not None:
        size = size or style.size
        color = color or style.color
        weight = weight or style.weight
        italic = italic if italic is not None else style.italic
        font_family = font_family or style.font_family
    if font_family:
        f["family"] = _g._family(font_family)
    if size:
        f["size"] = float(size)
    if weight and str(weight) in ("bold", "w600", "w700", "w800", "w900"):
        f["bold"] = True
    elif weight and str(weight) == "w500":
        f["medium"] = True
    if italic:
        f["italic"] = True
    if len(f) > 1 or font_family:
        p["font"] = f
    c = _g.color(color)
    if c:
        p["fg"] = c
    bg = _g.color(bgcolor)
    if bg:
        p["bg"] = bg
    if align:
        p["justify"] = {"start": "left", "end": "right", "justify": "left"}.get(str(align), str(align))
    return p


# ===================================================================== 表示する部品

class Text(Control):
    _type = "label"

    def __init__(self, value=None, size=None, color=None, bgcolor=None, weight=None, italic=None,
                 text_align=None, max_lines=None, overflow=None, selectable=None, font_family=None,
                 style=None, theme_style=None, no_wrap=None, spans=None, semantics_label=None, **kw):
        Control.__init__(self, value=value, size=size, color=color, bgcolor=bgcolor, weight=weight,
                         italic=italic, text_align=text_align, max_lines=max_lines, overflow=overflow,
                         selectable=selectable, font_family=font_family, style=style,
                         theme_style=theme_style, no_wrap=no_wrap, spans=spans, **kw)

    _THEME_SIZES = {"displaylarge": 57, "displaymedium": 45, "displaysmall": 36, "headlinelarge": 32,
                    "headlinemedium": 28, "headlinesmall": 24, "titlelarge": 22, "titlemedium": 16,
                    "titlesmall": 14, "bodylarge": 16, "bodymedium": 14, "bodysmall": 12,
                    "labellarge": 14, "labelmedium": 12, "labelsmall": 11}

    def _props(self):
        p = Control._props(self)
        value = self.value
        if value is None and self.spans:
            value = "".join(str(getattr(s, "text", "")) for s in self.spans)
        size = self.size
        if size is None and self.theme_style:
            size = self._THEME_SIZES.get(str(self.theme_style).lower().replace("_", "").replace(".", ""))
        _text_props(p, value, size, self.color, self.weight, self.italic, self.font_family,
                    self.style, self.bgcolor, self.text_align)
        if self.max_lines:
            p["maxlines"] = int(self.max_lines)
        if self.no_wrap:
            p["maxlines"] = 1
        if self.selectable:
            p["selectable"] = True
        if self.text_align in ("center", TextAlign.CENTER):
            p["anchor"] = "center"
        return p


class TextSpan:
    def __init__(self, text="", style=None, spans=None, on_click=None, url=None, **kw):
        self.text, self.style, self.spans = text, style, spans


class Markdown(Text):
    def __init__(self, value="", selectable=False, extension_set=None, code_theme=None,
                 on_tap_link=None, **kw):
        import re
        plain = re.sub(r"^#{1,6}\s*", "", str(value), flags=re.M)
        plain = re.sub(r"\*\*(.+?)\*\*|__(.+?)__", lambda m: m.group(1) or m.group(2), plain)
        plain = re.sub(r"\[(.+?)\]\((.+?)\)", r"\1", plain)
        plain = re.sub(r"`([^`]*)`", r"\1", plain)
        Text.__init__(self, plain, selectable=selectable, **kw)


_ICON_GLYPHS = {
    "add": "＋", "remove": "－", "delete": "🗑", "delete_outline": "🗑", "edit": "✎", "close": "✕",
    "check": "✓", "done": "✓", "clear": "✕", "search": "🔍", "home": "⌂", "settings": "⚙",
    "menu": "☰", "more_vert": "⋮", "more_horiz": "⋯", "arrow_back": "←", "arrow_forward": "→",
    "arrow_upward": "↑", "arrow_downward": "↓", "chevron_left": "‹", "chevron_right": "›",
    "expand_more": "⌄", "expand_less": "⌃", "refresh": "↻", "favorite": "♥", "favorite_border": "♡",
    "star": "★", "star_border": "☆", "info": "ⓘ", "info_outline": "ⓘ", "warning": "⚠", "error": "⚠",
    "help": "?", "play_arrow": "▶", "pause": "❚❚", "stop": "■", "send": "➤", "share": "⇪",
    "person": "👤", "people": "👥", "email": "✉", "mail": "✉", "phone": "☎", "lock": "🔒",
    "lock_open": "🔓", "visibility": "👁", "light_mode": "☀", "dark_mode": "☾", "wb_sunny": "☀",
    "cloud": "☁", "music_note": "♪", "calendar_today": "📅", "access_time": "⏱", "timer": "⏱",
    "alarm": "⏰", "notifications": "🔔", "shopping_cart": "🛒", "attach_file": "📎", "folder": "📁",
    "folder_open": "📂", "insert_drive_file": "📄", "description": "📄", "save": "💾", "upload": "⇧",
    "download": "⇩", "file_upload": "⇧", "file_download": "⇩", "camera_alt": "📷", "image": "🖼",
    "photo": "🖼", "language": "🌐", "public": "🌐", "location_on": "📍", "map": "🗺", "thumb_up": "👍",
    "thumb_down": "👎", "check_circle": "✔", "cancel": "✖", "radio_button_checked": "◉",
    "radio_button_unchecked": "○", "check_box": "☑", "check_box_outline_blank": "☐", "copy": "⧉",
    "content_copy": "⧉", "content_paste": "📋", "logout": "⇥", "login": "⇤", "exit_to_app": "⇥",
    "account_circle": "◉", "code": "</>", "computer": "🖥", "smartphone": "📱", "battery_full": "🔋",
    "wifi": "📶", "bluetooth": "ᛒ", "volume_up": "🔊", "volume_off": "🔇", "mic": "🎤", "flag": "⚑",
    "bookmark": "🔖", "bookmark_border": "🔖", "label": "🏷", "list": "☰", "grid_view": "▦",
    "dashboard": "▦", "filter_list": "⏷", "sort": "⇅", "undo": "↶", "redo": "↷", "zoom_in": "⊕",
    "zoom_out": "⊖", "fullscreen": "⛶", "lightbulb": "💡", "bolt": "⚡", "rocket_launch": "🚀",
    "celebration": "🎉", "pets": "🐾", "restaurant": "🍴", "local_cafe": "☕", "sports_esports": "🎮",
    "calculate": "🧮", "translate": "あ", "keyboard": "⌨", "build": "🔧", "palette": "🎨",
}


def _glyph(name):
    if not name:
        return ""
    n = str(name).lower()
    for suffix in ("_outlined", "_rounded", "_sharp"):
        if n.endswith(suffix):
            n = n[:-len(suffix)]
    return _ICON_GLYPHS.get(n, "●")


class Icon(Control):
    _type = "label"

    def __init__(self, name=None, color=None, size=None, semantics_label=None, **kw):
        Control.__init__(self, name=name or kw.pop("icon", None), color=color, size=size, **kw)

    def _props(self):
        p = Control._props(self)
        p["text"] = _glyph(self.name)
        p["font"] = {"family": "sans", "size": float(self.size or 22)}
        c = _g.color(self.color)
        if c:
            p["fg"] = c
        p["anchor"] = "center"
        return p


class Image(Control):
    _type = "label"

    def __init__(self, src=None, src_base64=None, width=None, height=None, fit=None, border_radius=None,
                 repeat=None, error_content=None, tooltip=None, color=None, **kw):
        Control.__init__(self, src=src, src_base64=src_base64, width=width, height=height, fit=fit,
                         border_radius=border_radius, tooltip=tooltip, **kw)

    def _props(self):
        p = Control._props(self)
        ref = _g.image_ref(self.src, self.src_base64) if (self.src or self.src_base64) else None
        if ref and "path" in ref and not os.path.exists(ref["path"]):
            alt = os.path.join(os.getcwd(), "assets", str(self.src).lstrip("/"))
            if os.path.exists(alt):
                ref["path"] = alt
        if ref:
            p["image"] = ref
            p["compound"] = "none"
            p["fit"] = str(self.fit or "contain")
        r = _radius(self.border_radius)
        if r:
            p["radius"] = r
        return p


class CircleAvatar(Control):
    _type = "frame"

    def __init__(self, content=None, foreground_image_src=None, background_image_src=None,
                 bgcolor=None, color=None, radius=None, **kw):
        Control.__init__(self, content=content, bgcolor=bgcolor, color=color, radius=radius,
                         foreground_image_src=foreground_image_src or background_image_src, **kw)

    def _props(self):
        p = Control._props(self)
        size = float(self.radius or 20) * 2
        p["width"] = p["height"] = size
        p["radius"] = size / 2
        p["bg"] = _g.color(self.bgcolor) or "#d3e3fd"
        p["orient"] = "v"
        p["main"] = "center"
        p["cross"] = "center"
        return p

    def _children(self):
        if self.foreground_image_src:
            return [Image(src=self.foreground_image_src, width=float(self.radius or 20) * 2,
                          height=float(self.radius or 20) * 2, border_radius=self.radius or 20)]
        return [self.content] if self.content is not None else []

    def _child_opts(self, child, index):
        return {"m": "box", "weight": 0, "expand": False, "align": "center"}


class Divider(Control):
    _type = "separator"
    _fill_cross = True

    def __init__(self, height=None, thickness=None, color=None, leading_indent=None, trailing_indent=None, **kw):
        Control.__init__(self, height=height, thickness=thickness, color=color, **kw)

    def _props(self):
        p = Control._props(self)
        p.pop("height", None)
        p["orient"] = "horizontal"
        p["margin"] = float(self.height or 16) / 2
        if self.thickness:
            p["thickness"] = float(self.thickness)
        c = _g.color(self.color)
        if c:
            p["fg"] = c
        return p


class VerticalDivider(Divider):
    _fill_cross = False

    def _props(self):
        p = Divider._props(self)
        p["orient"] = "vertical"
        p["margin"] = float(self.width or 16) / 2
        p.pop("width", None)
        return p


class ProgressBar(Control):
    _type = "progress"
    _fill_cross = True

    def __init__(self, value=None, width=None, bar_height=None, color=None, bgcolor=None, **kw):
        Control.__init__(self, value=value, width=width, bar_height=bar_height, color=color,
                         bgcolor=bgcolor, **kw)

    def _props(self):
        p = Control._props(self)
        if self.value is None:
            p["mode"] = "indeterminate"
            p["running"] = True
        else:
            p["mode"] = "determinate"
            p["value"] = float(self.value) * 100
            p["max"] = 100
        c = _g.color(self.color)
        if c:
            p["fg"] = c
        return p


class ProgressRing(ProgressBar):
    _type = "ring"
    _fill_cross = False


class Placeholder(Control):
    _type = "frame"

    def _props(self):
        p = Control._props(self)
        p["border"] = {"width": 1, "color": "#747775"}
        p.setdefault("height", 100)
        return p


# ===================================================================== 入力の部品

class TextField(Control):
    _type = "entry"
    _fill_cross = True

    def __init__(self, value="", label=None, hint_text=None, password=False, can_reveal_password=False,
                 multiline=False, min_lines=None, max_lines=None, read_only=False, keyboard_type=None,
                 on_change=None, on_submit=None, on_focus=None, on_blur=None, prefix_text=None,
                 suffix_text=None, prefix_icon=None, suffix_icon=None, autofocus=False, error_text=None,
                 helper_text=None, text_align=None, text_size=None, color=None, bgcolor=None,
                 border=None, filled=None, max_length=None, capitalization=None, dense=None,
                 border_radius=None, input_filter=None, **kw):
        Control.__init__(self, value="" if value is None else str(value), label=label, hint_text=hint_text,
                         password=password, can_reveal_password=can_reveal_password, multiline=multiline,
                         min_lines=min_lines, max_lines=max_lines, read_only=read_only,
                         keyboard_type=keyboard_type, on_change=on_change, on_submit=on_submit,
                         on_focus=on_focus, on_blur=on_blur, prefix_text=prefix_text,
                         suffix_text=suffix_text, autofocus=autofocus, error_text=error_text,
                         helper_text=helper_text, text_align=text_align, text_size=text_size,
                         color=color, bgcolor=bgcolor, max_length=max_length, filled=filled,
                         prefix_icon=prefix_icon, suffix_icon=suffix_icon, input_filter=input_filter, **kw)

    def _props(self):
        p = Control._props(self)
        self.__dict__["_type"] = "text" if self.multiline else "entry"
        p["value"] = "" if self.value is None else str(self.value)
        p["outlined"] = True
        if self.label:
            p["label"] = str(self.label.value if isinstance(self.label, Text) else self.label)
        if self.hint_text:
            p["hint"] = str(self.hint_text)
        if self.password:
            p["password"] = True
            if self.can_reveal_password:
                p["reveal"] = True
        if self.read_only:
            p["state"] = "readonly"
        if self.keyboard_type:
            p["keyboard"] = str(self.keyboard_type)
        if self.multiline:
            p["hlines"] = int(self.min_lines or 3)
            if self.max_lines:
                p["maxlines"] = int(self.max_lines)
        if self.prefix_text:
            p["prefix"] = str(self.prefix_text)
        if self.suffix_text:
            p["suffix"] = str(self.suffix_text)
        if self.error_text:
            p["error"] = str(self.error_text)
        if self.helper_text:
            p["helper"] = str(self.helper_text)
        if self.max_length:
            p["maxlen"] = int(self.max_length)
        if self.text_align:
            p["justify"] = str(self.text_align)
        f = {}
        if self.text_size:
            f = {"family": "sans", "size": float(self.text_size)}
            p["font"] = f
        c = _g.color(self.color)
        if c:
            p["fg"] = c
        bg = _g.color(self.bgcolor)
        if bg:
            p["bg"] = bg
        if self.autofocus:
            p["autofocus"] = True
        return p

    def _on_android(self, ev):
        kind = ev.get("ev")
        if kind == "value":
            self.__dict__["value"] = str(ev.get("value", ""))
            self.__dict__["_sent"]["value"] = self.value
            self._fire(self.on_change, self.value, "change")
        elif kind == "submit":
            self._fire(self.on_submit, self.value, "submit")
        elif kind == "focus":
            self._fire(self.on_focus if ev.get("in") else self.on_blur, None, "focus" if ev.get("in") else "blur")


CupertinoTextField = TextField


class _Button(Control):
    _type = "button"
    _style = "filled"

    def __init__(self, text=None, icon=None, icon_color=None, on_click=None, content=None, bgcolor=None,
                 color=None, style=None, on_long_press=None, autofocus=False, url=None, tooltip=None,
                 elevation=None, on_hover=None, **kw):
        Control.__init__(self, text=text, icon=icon, icon_color=icon_color, on_click=on_click,
                         content=content, bgcolor=bgcolor, color=color, style=style,
                         on_long_press=on_long_press, url=url, tooltip=tooltip, **kw)

    def _label(self):
        t = self.text
        if t is None and self.content is not None:
            if isinstance(self.content, Text):
                t = self.content.value
            elif isinstance(self.content, Icon):
                return _glyph(self.content.name)
            elif isinstance(self.content, str):
                t = self.content
            else:
                t = ""
        glyph = _glyph(self.icon) if self.icon else ""
        if glyph and t:
            return "%s  %s" % (glyph, t)
        return glyph or ("" if t is None else str(t))

    def _props(self):
        p = Control._props(self)
        p["text"] = self._label()
        p["style"] = self._style
        bg = _g.color(self.bgcolor) or (_g.color(self.style.bgcolor) if isinstance(self.style, ButtonStyle) else None)
        fg = _g.color(self.color) or (_g.color(self.style.color) if isinstance(self.style, ButtonStyle) else None)
        if bg:
            p["bg"] = bg
        if fg:
            p["fg"] = fg
        if isinstance(self.style, ButtonStyle) and isinstance(self.style.shape, RoundedRectangleBorder):
            p["radius"] = self.style.shape.radius
        return p

    def _on_android(self, ev):
        if ev.get("ev") == "command":
            if self.url:
                _g.bridge().send({"op": "open_url", "url": str(self.url)})
            self._fire(self.on_click, None, "click")
        elif ev.get("ev") == "long":
            self._fire(self.on_long_press, None, "long_press")


class ElevatedButton(_Button):
    _style = "elevated"


class FilledButton(_Button):
    _style = "filled"


class FilledTonalButton(_Button):
    _style = "tonal"


class OutlinedButton(_Button):
    _style = "outlined"


class TextButton(_Button):
    _style = "text"


class Button(_Button):
    _style = "filled"


CupertinoButton = CupertinoFilledButton = Button


class IconButton(_Button):
    _style = "icon"

    def __init__(self, icon=None, icon_color=None, icon_size=None, on_click=None, selected=None,
                 selected_icon=None, **kw):
        _Button.__init__(self, icon=icon, icon_color=icon_color, on_click=on_click, **kw)
        self.icon_size = icon_size
        self.selected = selected
        self.selected_icon = selected_icon

    def _props(self):
        p = _Button._props(self)
        icon = self.selected_icon if self.selected and self.selected_icon else self.icon
        p["text"] = _glyph(icon) if icon else self._label()
        c = _g.color(self.icon_color)
        if c:
            p["fg"] = c
        if self.icon_size:
            p["font"] = {"family": "sans", "size": float(self.icon_size)}
        return p


class FloatingActionButton(_Button):
    _style = "fab"


class Checkbox(Control):
    _type = "check"

    def __init__(self, label=None, value=False, on_change=None, tristate=False, check_color=None,
                 fill_color=None, label_position=None, **kw):
        Control.__init__(self, label=label, value=value, on_change=on_change, tristate=tristate, **kw)

    def _props(self):
        p = Control._props(self)
        p["text"] = "" if self.label is None else str(self.label)
        p["value"] = bool(self.value)
        return p

    def _on_android(self, ev):
        if ev.get("ev") == "value":
            self.__dict__["value"] = bool(ev.get("value"))
            self.__dict__["_sent"]["value"] = self.value
            self._fire(self.on_change, "true" if self.value else "false", "change")


class Switch(Checkbox):
    _type = "switch"

    def __init__(self, label=None, value=False, on_change=None, active_color=None, **kw):
        Checkbox.__init__(self, label=label, value=value, on_change=on_change, **kw)


CupertinoSwitch = Switch
CupertinoCheckbox = Checkbox


class Radio(Control):
    _type = "radio"

    def __init__(self, value=None, label=None, **kw):
        Control.__init__(self, value=value, label=label, **kw)

    def _group(self):
        p = self._parent
        while p is not None and not isinstance(p, RadioGroup):
            p = p._parent
        return p

    def _props(self):
        p = Control._props(self)
        p["text"] = "" if self.label is None else str(self.label)
        g = self._group()
        p["value"] = g is not None and g.value is not None and str(g.value) == str(self.value)
        return p

    def _on_android(self, ev):
        if ev.get("ev") == "value" and ev.get("value"):
            g = self._group()
            if g is not None:
                g.__dict__["value"] = self.value
                g._fire(g.on_change, self.value, "change")
                if self.page is None or not hasattr(self.page, "update"):
                    g.update()


class RadioGroup(Control):
    _type = "frame"

    def __init__(self, content=None, value=None, on_change=None, **kw):
        Control.__init__(self, content=content, value=value, on_change=on_change, **kw)

    def _props(self):
        p = Control._props(self)
        p["orient"] = "v"
        return p

    def _children(self):
        return [self.content] if self.content is not None else []

    def _child_opts(self, child, index):
        return {"m": "box", "weight": 0, "expand": True}


class Slider(Control):
    _type = "scale"
    _fill_cross = True

    def __init__(self, value=None, min=0, max=1, divisions=None, label=None, on_change=None,
                 on_change_start=None, on_change_end=None, active_color=None, round=None, **kw):
        Control.__init__(self, value=value if value is not None else min, min=min, max=max,
                         divisions=divisions, label=label, on_change=on_change,
                         on_change_end=on_change_end, round=round, **kw)

    def _props(self):
        p = Control._props(self)
        lo, hi = float(self.min or 0), float(self.max if self.max is not None else 1)
        p["from"], p["to"] = lo, hi
        p["step"] = (hi - lo) / self.divisions if self.divisions else 0
        p["value"] = float(self.value or 0)
        p["orient"] = "horizontal"
        p["showvalue"] = bool(self.label)
        return p

    def _on_android(self, ev):
        if ev.get("ev") == "value":
            v = float(ev.get("value", 0))
            if self.round is not None:
                v = round(v, int(self.round))
            self.__dict__["value"] = v
            self.__dict__["_sent"]["value"] = v
            self._fire(self.on_change, str(v), "change")
            if ev.get("end"):
                self._fire(self.on_change_end, str(v), "change_end")


CupertinoSlider = Slider


class _DropdownOption:
    def __init__(self, key=None, text=None, content=None, disabled=False, **kw):
        self.key = key if key is not None else text
        self.text = text if text is not None else (str(key) if key is not None else "")


class _DropdownNS:
    Option = _DropdownOption


dropdown = _DropdownNS()
DropdownOption = _DropdownOption


class Dropdown(Control):
    _type = "combo"
    _fill_cross = True

    def __init__(self, value=None, options=None, label=None, hint_text=None, on_change=None,
                 autofocus=False, editable=False, **kw):
        Control.__init__(self, value=value, options=options or [], label=label, hint_text=hint_text,
                         on_change=on_change, editable=editable, **kw)

    def _props(self):
        p = Control._props(self)
        opts = self.options or []
        p["items"] = [str(o.text if isinstance(o, _DropdownOption) else o) for o in opts]
        cur = ""
        for o in opts:
            key = o.key if isinstance(o, _DropdownOption) else o
            if self.value is not None and str(key) == str(self.value):
                cur = str(o.text if isinstance(o, _DropdownOption) else o)
        p["value"] = cur
        p["readonly"] = not self.editable
        if self.label:
            p["label"] = str(self.label)
        if self.hint_text:
            p["hint"] = str(self.hint_text)
        return p

    def _on_android(self, ev):
        if ev.get("ev") == "value":
            text = str(ev.get("value", ""))
            for o in self.options or []:
                t = str(o.text if isinstance(o, _DropdownOption) else o)
                if t == text:
                    self.__dict__["value"] = o.key if isinstance(o, _DropdownOption) else o
                    break
            else:
                if self.editable:
                    self.__dict__["value"] = text
            self.__dict__["_sent"]["value"] = text
            self._fire(self.on_change, self.value, "change")


DropdownM2 = Dropdown


# ===================================================================== 並べる部品

def _main_align(a):
    return str(a or "start")


class _Flex(Control):
    _orient = "v"

    def __init__(self, controls=None, alignment=None, spacing=None, wrap=False, scroll=None,
                 run_spacing=None, tight=False, auto_scroll=False, on_scroll=None, **kw):
        Control.__init__(self, controls=list(controls) if controls is not None else [], alignment=alignment,
                         spacing=10 if spacing is None else spacing, wrap=wrap, scroll=scroll,
                         run_spacing=run_spacing, tight=tight, cross_alignment=None, **kw)

    def _props(self):
        p = Control._props(self)
        p["orient"] = self._orient
        p["spacing"] = float(self.spacing or 0)
        p["main"] = _main_align(self.alignment)
        p["cross"] = str(self.cross_alignment or ("center" if self._orient == "h" else "start"))
        if self.wrap:
            p["wrap"] = True
        if self.scroll:
            p["scroll"] = self._orient
        return p

    def _children(self):
        return list(self.controls or [])

    def _child_opts(self, child, index):
        cross = str(self.cross_alignment or ("center" if self._orient == "h" else "start"))
        fill = cross == "stretch" or (self._orient == "v" and getattr(child, "_fill_cross", False)
                                      and child.width is None)
        return {"m": "box", "weight": child._expand_weight(), "expand": fill,
                "align": {"start": "start", "end": "end", "center": "center"}.get(cross, "start")}

    def __setattr__(self, name, value):
        # Column: 縦が主軸(alignment)、横が交差軸。Row はその逆
        if name == "horizontal_alignment":
            if value is None:
                return
            name = "cross_alignment" if self._orient == "v" else "alignment"
        elif name == "vertical_alignment":
            if value is None:
                return
            name = "alignment" if self._orient == "v" else "cross_alignment"
        self.__dict__[name] = value


class Column(_Flex):
    _orient = "v"


class Row(_Flex):
    _orient = "h"
    _fill_cross = True  # Flutter の Row と同じく、Column の中では横いっぱいに広がる


class ResponsiveRow(Row):
    def __init__(self, controls=None, **kw):
        Row.__init__(self, controls, wrap=True, **kw)


class ListView(_Flex):
    _orient = "v"
    _fill_cross = True

    def __init__(self, controls=None, spacing=0, padding=None, horizontal=False, divider_thickness=0,
                 auto_scroll=False, item_extent=None, first_item_prototype=False, **kw):
        _Flex.__init__(self, controls, spacing=spacing, **kw)
        self.padding = padding
        self.horizontal = horizontal
        self.auto_scroll = auto_scroll
        self.cross_alignment = "stretch"

    def _props(self):
        p = _Flex._props(self)
        p["orient"] = "h" if self.horizontal else "v"
        p["scroll"] = "h" if self.horizontal else "v"
        pad = _pad(self.padding)
        if pad:
            p["padding"] = pad
        if self.auto_scroll:
            p["autoscroll"] = True
        return p


class GridView(Control):
    _type = "frame"
    _fill_cross = True

    def __init__(self, controls=None, runs_count=None, max_extent=None, child_aspect_ratio=1.0,
                 spacing=10, run_spacing=10, padding=None, horizontal=False, **kw):
        Control.__init__(self, controls=list(controls) if controls is not None else [],
                         runs_count=runs_count, max_extent=max_extent, child_aspect_ratio=child_aspect_ratio,
                         spacing=spacing, run_spacing=run_spacing, padding=padding, **kw)

    def _ncols(self):
        if self.runs_count:
            return int(self.runs_count)
        w = float(self.width or os.environ.get("AIRSKY_SCREEN_W", 400))
        return max(1, int(w // float(self.max_extent or 150)))

    def _props(self):
        p = Control._props(self)
        n = self._ncols()
        p["cols"] = {str(i): [1.0, 0, 0] for i in range(n)}
        p["scroll"] = "v"
        pad = _pad(self.padding)
        if pad:
            p["padding"] = pad
        return p

    def _children(self):
        return list(self.controls or [])

    def _child_opts(self, child, index):
        n = self._ncols()
        r, c = divmod(index, n)
        sp, rsp = float(self.spacing or 0) / 2, float(self.run_spacing or 0) / 2
        return {"m": "grid", "row": r, "col": c, "sticky": "nsew", "padx": [sp, sp], "pady": [rsp, rsp],
                "aspect": float(self.child_aspect_ratio or 1.0)}


class Stack(Control):
    _type = "frame"

    def __init__(self, controls=None, alignment=None, fit=None, **kw):
        Control.__init__(self, controls=list(controls) if controls is not None else [], alignment=alignment, **kw)

    def _children(self):
        return list(self.controls or [])

    def _child_opts(self, child, index):
        o = {"m": "place", "anchor": "nw", "x": 0, "y": 0}
        if child.left is not None:
            o["x"] = float(child.left)
        if child.top is not None:
            o["y"] = float(child.top)
        if child.right is not None:
            o["relx"] = 1.0
            o["x"] = -float(child.right)
            o["anchor"] = "ne" if child.top is not None or child.bottom is None else "se"
        if child.bottom is not None:
            o["rely"] = 1.0
            o["y"] = -float(child.bottom)
            o["anchor"] = ("s" + ("e" if child.right is not None else "w"))
        if child.left is None and child.top is None and child.right is None and child.bottom is None:
            a = self.alignment
            if isinstance(a, Alignment):
                o.update({"relx": (a.x + 1) / 2, "rely": (a.y + 1) / 2,
                          "anchor": _anchor_of(a)})
            if child._expand_weight():
                o.update({"relw": 1.0, "relh": 1.0})
        return o


def _anchor_of(a):
    v = "n" if a.y < 0 else ("s" if a.y > 0 else "")
    h = "w" if a.x < 0 else ("e" if a.x > 0 else "")
    return (v + h) or "center"


class Container(Control):
    _type = "frame"

    def __init__(self, content=None, padding=None, margin=None, bgcolor=None, border_radius=None,
                 border=None, width=None, height=None, alignment=None, on_click=None, on_long_press=None,
                 on_hover=None, ink=False, gradient=None, image_src=None, shadow=None, shape=None,
                 clip_behavior=None, blur=None, animate=None, **kw):
        Control.__init__(self, content=content, padding=padding, margin=margin, bgcolor=bgcolor,
                         border_radius=border_radius, border=border, width=width, height=height,
                         alignment=alignment, on_click=on_click, on_long_press=on_long_press, ink=ink,
                         gradient=gradient, shape=shape, **kw)

    def _props(self):
        p = Control._props(self)
        bg = _g.color(self.bgcolor)
        if not bg and isinstance(self.gradient, LinearGradient) and self.gradient.colors:
            bg = _g.color(self.gradient.colors[0])
        if bg:
            p["bg"] = bg
        r = _radius(self.border_radius)
        if r:
            p["radius"] = r
        if str(self.shape or "").lower().endswith("circle"):
            p["radius"] = 9999
        b = self.border
        if isinstance(b, Border):
            side = b.top or b.left or b.right or b.bottom
            if side is not None:
                p["border"] = {"width": float(side.width or 1), "color": _g.color(side.color) or "#747775"}
        pad = _pad(self.padding)
        if pad:
            p["padding"] = pad
        m = _pad(self.margin)
        if m:
            p["margin"] = m
        if self.on_click or self.on_long_press:
            p["clickable"] = True
        p["orient"] = "v"
        a = self.alignment
        if isinstance(a, Alignment):
            p["main"] = "start" if a.y < 0 else ("end" if a.y > 0 else "center")
            p["cross"] = "start" if a.x < 0 else ("end" if a.x > 0 else "center")
        return p

    def _children(self):
        return [self.content] if isinstance(self.content, Control) else []

    def _child_opts(self, child, index):
        a = self.alignment
        align = "start"
        if isinstance(a, Alignment):
            align = "start" if a.x < 0 else ("end" if a.x > 0 else "center")
        # 大きさの決まった Container(alignment なし)では、中身は全体に広がる(Flutter と同じ)
        fill = a is None and (self.width is not None or self.height is not None)
        weight = 1.0 if (fill and self.height is not None) else child._expand_weight()
        return {"m": "box", "weight": weight, "align": align,
                "expand": fill or (a is None and (child._fill_cross or child._expand_weight() > 0))}

    def _on_android(self, ev):
        if ev.get("ev") == "command":
            self._fire(self.on_click, None, "click")
        elif ev.get("ev") == "long":
            self._fire(self.on_long_press, None, "long_press")


class Card(Container):
    def __init__(self, content=None, elevation=None, color=None, margin=None, shape=None,
                 surface_tint_color=None, variant=None, **kw):
        Container.__init__(self, content=content, bgcolor=color, margin=margin, **kw)
        self.elevation = elevation

    def _props(self):
        p = Container._props(self)
        p.setdefault("bg", "@surfaceContainer")
        p.setdefault("radius", 12)
        p["elevation"] = float(self.elevation if self.elevation is not None else 1)
        return p


class SafeArea(Container):
    def __init__(self, content=None, **kw):
        Container.__init__(self, content=content, expand=kw.pop("expand", None), **kw)


class Semantics(Container):
    pass


class GestureDetector(Container):
    def __init__(self, content=None, on_tap=None, on_double_tap=None, on_long_press=None,
                 on_pan_start=None, on_pan_update=None, on_pan_end=None, on_tap_down=None,
                 on_tap_up=None, on_hover=None, mouse_cursor=None, drag_interval=None, **kw):
        Container.__init__(self, content=content, **kw)
        self.on_tap, self.on_double_tap, self.on_long_press = on_tap, on_double_tap, on_long_press
        self.on_pan_start, self.on_pan_update, self.on_pan_end = on_pan_start, on_pan_update, on_pan_end
        self.on_tap_down, self.on_tap_up = on_tap_down, on_tap_up
        self.__dict__["_last"] = (0, 0)

    def _props(self):
        p = Container._props(self)
        p["mouse"] = True
        return p

    def _on_android(self, ev):
        if ev.get("ev") != "mouse":
            return
        t = ev.get("type")
        x, y = float(ev.get("x", 0)), float(ev.get("y", 0))
        if t == "press":
            self.__dict__["_last"] = (x, y)
            _call_handler(self.on_tap_down, TapEvent(x, y, self, self.page), self.page)
            _call_handler(self.on_pan_start, TapEvent(x, y, self, self.page), self.page)
        elif t == "motion":
            lx, ly = self._last
            self.__dict__["_last"] = (x, y)
            _call_handler(self.on_pan_update, DragUpdateEvent(x, y, x - lx, y - ly, self, self.page), self.page)
        elif t == "release":
            _call_handler(self.on_pan_end, TapEvent(x, y, self, self.page), self.page)
            _call_handler(self.on_tap_up, TapEvent(x, y, self, self.page), self.page)
            if ev.get("click"):
                _call_handler(self.on_tap, TapEvent(x, y, self, self.page), self.page)
        elif t == "double":
            _call_handler(self.on_double_tap, TapEvent(x, y, self, self.page), self.page)
        elif t == "long":
            _call_handler(self.on_long_press, TapEvent(x, y, self, self.page), self.page)


class ListTile(Control):
    _type = "frame"
    _fill_cross = True

    def __init__(self, title=None, subtitle=None, leading=None, trailing=None, on_click=None,
                 on_long_press=None, dense=False, selected=False, bgcolor=None, content_padding=None,
                 is_three_line=False, toggle_inputs=False, **kw):
        Control.__init__(self, title=title, subtitle=subtitle, leading=leading, trailing=trailing,
                         on_click=on_click, on_long_press=on_long_press, dense=dense, selected=selected,
                         bgcolor=bgcolor, content_padding=content_padding, **kw)
        self.__dict__["_texts"] = None

    def _props(self):
        p = Control._props(self)
        p["orient"] = "h"
        p["spacing"] = 16
        p["cross"] = "center"
        p["padding"] = _pad(self.content_padding) or [16, 8 if self.dense else 10, 16, 8 if self.dense else 10]
        bg = _g.color(self.bgcolor)
        if self.selected:
            bg = bg or "#d3e3fd"
        if bg:
            p["bg"] = bg
        if self.on_click or self.on_long_press:
            p["clickable"] = True
        return p

    def _children(self):
        if self._texts is None:
            self.__dict__["_texts"] = Column([], spacing=2, expand=True)
        col = self._texts
        items = []
        if self.title is not None:
            items.append(self.title if isinstance(self.title, Control) else Text(str(self.title), size=16))
        if self.subtitle is not None:
            items.append(self.subtitle if isinstance(self.subtitle, Control)
                         else Text(str(self.subtitle), size=14, color="onsurfacevariant"))
        col.controls = items
        out = []
        if isinstance(self.leading, Control):
            out.append(self.leading)
        out.append(col)
        if isinstance(self.trailing, Control):
            out.append(self.trailing)
        return out

    def _child_opts(self, child, index):
        return {"m": "box", "weight": 1.0 if child is self._texts else 0.0, "expand": False, "align": "center"}

    def _on_android(self, ev):
        if ev.get("ev") == "command":
            self._fire(self.on_click, None, "click")
        elif ev.get("ev") == "long":
            self._fire(self.on_long_press, None, "long_press")


class ExpansionTile(ListTile):
    def __init__(self, title=None, subtitle=None, controls=None, initially_expanded=False, **kw):
        ListTile.__init__(self, title=title, subtitle=subtitle, **kw)
        self.controls = controls or []


class Tab:
    def __init__(self, text=None, content=None, icon=None, tab_content=None, label=None, **kw):
        self.text = text if text is not None else label
        self.content = content
        self.icon = icon


class Tabs(Control):
    _type = "notebook"
    _fill_cross = True

    def __init__(self, tabs=None, selected_index=0, on_change=None, animation_duration=None,
                 scrollable=None, **kw):
        Control.__init__(self, tabs=list(tabs or []), selected_index=selected_index, on_change=on_change, **kw)
        self.__dict__["_holders"] = {}

    def _props(self):
        p = Control._props(self)
        p["tabs"] = [(_glyph(t.icon) + " " if t.icon else "") + str(t.text or "") for t in self.tabs]
        p["selected"] = int(self.selected_index or 0)
        return p

    def _children(self):
        out = []
        for t in self.tabs:
            h = self._holders.get(id(t))
            if h is None:
                h = Container(expand=True)
                self._holders[id(t)] = h
            h.content = t.content
            out.append(h)
        return out

    def _child_opts(self, child, index):
        return {"m": "tab"}

    def _on_android(self, ev):
        if ev.get("ev") == "tab":
            self.__dict__["selected_index"] = int(ev.get("index", 0))
            self.__dict__["_sent"]["selected"] = self.selected_index
            self._fire(self.on_change, str(self.selected_index), "change")


class AppBar(Control):
    def __init__(self, title=None, leading=None, actions=None, bgcolor=None, center_title=False,
                 color=None, toolbar_height=None, **kw):
        Control.__init__(self, title=title, leading=leading, actions=actions or [], bgcolor=bgcolor, **kw)


CupertinoAppBar = AppBar


class NavigationBarDestination:
    def __init__(self, icon=None, label=None, selected_icon=None, **kw):
        self.icon, self.label = icon, label


NavigationDestination = NavigationBarDestination
NavigationRailDestination = NavigationBarDestination


class NavigationBar(Control):
    _type = "notebook"
    _fill_cross = True

    def __init__(self, destinations=None, selected_index=0, on_change=None, bgcolor=None, **kw):
        Control.__init__(self, destinations=list(destinations or []), selected_index=selected_index,
                         on_change=on_change, **kw)

    def _props(self):
        p = Control._props(self)
        p["tabs"] = [(_glyph(d.icon) + " " if d.icon else "") + str(d.label or "") for d in self.destinations]
        p["selected"] = int(self.selected_index or 0)
        p["bar"] = True
        return p

    def _on_android(self, ev):
        if ev.get("ev") == "tab":
            self.__dict__["selected_index"] = int(ev.get("index", 0))
            self.__dict__["_sent"]["selected"] = self.selected_index
            self._fire(self.on_change, str(self.selected_index), "change")


NavigationRail = CupertinoNavigationBar = NavigationBar


class Chip(_Button):
    _style = "outlined"

    def __init__(self, label=None, on_click=None, leading=None, selected=False, on_select=None, **kw):
        _Button.__init__(self, text=label.value if isinstance(label, Text) else label, on_click=on_click or on_select, **kw)
        self.selected = selected


class Banner(Control):
    pass


# ===================================================================== ダイアログ・通知

class AlertDialog(Control):
    _type = "window"

    def __init__(self, title=None, content=None, actions=None, modal=False, open=False, on_dismiss=None,
                 actions_alignment=None, **kw):
        Control.__init__(self, title=title, content=content, actions=actions or [], modal=modal,
                         open=open, on_dismiss=on_dismiss, actions_alignment=actions_alignment, **kw)
        self.__dict__["_body"] = None

    def _props(self):
        p = {"toplevel": True, "dialog": True, "visible": bool(self.open)}
        t = self.title
        p["title"] = t.value if isinstance(t, Text) else ("" if t is None else str(t))
        p["modal"] = bool(self.modal)
        return p

    def _children(self):
        if self._body is None:
            self.__dict__["_body"] = Column([], spacing=16, tight=True, horizontal_alignment="stretch")
        items = []
        if isinstance(self.content, Control):
            items.append(self.content)
        elif self.content is not None:
            items.append(Text(str(self.content)))
        if self.actions:
            row = self.__dict__.get("_actions_row")
            if row is None:
                row = Row([], spacing=8)
                self.__dict__["_actions_row"] = row
            row.controls = list(self.actions)
            row.alignment = self.actions_alignment or MainAxisAlignment.END
            items.append(row)
        self._body.controls = items
        return [self._body]

    def _child_opts(self, child, index):
        return {"m": "box", "weight": 0, "expand": True, "margin": [20, 8, 20, 16]}

    def _on_android(self, ev):
        if ev.get("ev") == "close":
            if not self.modal:
                self.__dict__["open"] = False
                self.__dict__["_sent"]["visible"] = False
                _g.bridge().send({"op": "set", "id": self._id, "props": {"visible": False}})
                self._fire(self.on_dismiss, None, "dismiss")


CupertinoAlertDialog = AlertDialog


class BottomSheet(AlertDialog):
    def __init__(self, content=None, open=False, on_dismiss=None, **kw):
        AlertDialog.__init__(self, content=content, open=open, on_dismiss=on_dismiss, **kw)


CupertinoBottomSheet = BottomSheet


class SnackBar(Control):
    def __init__(self, content=None, open=False, action=None, on_action=None, duration=None, bgcolor=None, **kw):
        Control.__init__(self, content=content, open=open, action=action, on_action=on_action, **kw)

    def _show(self):
        c = self.content
        text = c.value if isinstance(c, Text) else ("" if c is None else str(c))
        _g.bridge().toast(text)
        self.__dict__["open"] = False


class FilePickerFile:
    def __init__(self, name, path, size, id=None):
        self.name, self.path, self.size, self.id = name, path, size, id


class FilePickerResultEvent(ControlEvent):
    def __init__(self, path=None, files=None, page=None):
        ControlEvent.__init__(self, page=page, name="result")
        self.path = path
        self.files = files


class FilePickerFileType:
    ANY = "any"
    MEDIA = "media"
    IMAGE = "image"
    VIDEO = "video"
    AUDIO = "audio"
    CUSTOM = "custom"


class FilePicker(Control):
    def __init__(self, on_result=None, **kw):
        Control.__init__(self, on_result=on_result, **kw)
        self.result = None

    def _ask(self, kind, **kw):
        r = _g.bridge().dialog(kind, **kw)
        return r

    def pick_files(self, dialog_title=None, initial_directory=None, file_type=None,
                   allowed_extensions=None, allow_multiple=False):
        types = [["", ["*." + e for e in allowed_extensions]]] if allowed_extensions else []
        r = self._ask("open", title=str(dialog_title or ""), multiple=bool(allow_multiple), filetypes=types,
                      initialdir=os.path.abspath(initial_directory or os.getcwd()))
        files = [FilePickerFile(os.path.basename(p), p, os.path.getsize(p) if os.path.exists(p) else 0)
                 for p in (r or [])] or None
        self.result = FilePickerResultEvent(None, files, self.page)
        _call_handler(self.on_result, self.result, self.page)
        return files

    def save_file(self, dialog_title=None, file_name=None, initial_directory=None, file_type=None,
                  allowed_extensions=None):
        r = self._ask("save", title=str(dialog_title or ""), initialfile=str(file_name or ""),
                      initialdir=os.path.abspath(initial_directory or os.getcwd()))
        self.result = FilePickerResultEvent(r or None, None, self.page)
        _call_handler(self.on_result, self.result, self.page)
        return r

    def get_directory_path(self, dialog_title=None, initial_directory=None):
        r = self._ask("open", title=str(dialog_title or ""), directory=True,
                      initialdir=os.path.abspath(initial_directory or os.getcwd()))
        path = r[0] if r else None
        self.result = FilePickerResultEvent(path, None, self.page)
        _call_handler(self.on_result, self.result, self.page)
        return path

    async def pick_files_async(self, *a, **kw):
        return self.pick_files(*a, **kw)


class Audio(Control):
    def play(self):
        pass

    def pause(self):
        pass


class HapticFeedback(Control):
    def heavy_impact(self):
        pass

    light_impact = medium_impact = vibrate = selection_click = heavy_impact


# ===================================================================== Page

class _ClientStorage:
    def __init__(self):
        self._path = os.path.join(os.getcwd(), ".flet_client_storage.json")

    def _load(self):
        try:
            with open(self._path, encoding="utf-8") as f:
                return json.load(f)
        except (OSError, ValueError):
            return {}

    def _save(self, d):
        with open(self._path, "w", encoding="utf-8") as f:
            json.dump(d, f, ensure_ascii=False, indent=1)

    def set(self, key, value):
        d = self._load()
        d[key] = value
        self._save(d)
        return True

    def get(self, key):
        return self._load().get(key)

    def contains_key(self, key):
        return key in self._load()

    def remove(self, key):
        d = self._load()
        d.pop(key, None)
        self._save(d)
        return True

    def get_keys(self, prefix=""):
        return [k for k in self._load() if k.startswith(prefix)]

    def clear(self):
        self._save({})
        return True

    async def set_async(self, key, value):
        return self.set(key, value)

    async def get_async(self, key):
        return self.get(key)


class _Session:
    def __init__(self):
        self._d = {}

    def set(self, key, value):
        self._d[key] = value

    def get(self, key):
        return self._d.get(key)

    def contains_key(self, key):
        return key in self._d

    def remove(self, key):
        self._d.pop(key, None)

    def clear(self):
        self._d.clear()

    def get_keys(self):
        return list(self._d)


class _Window:
    def __init__(self, page):
        self.__dict__["_page"] = page
        self.__dict__["width"] = None
        self.__dict__["height"] = None

    def __setattr__(self, name, value):
        self.__dict__[name] = value

    def __getattr__(self, name):
        return None

    def close(self):
        self._page.window_destroy()

    destroy = close

    def center(self):
        pass

    def to_front(self):
        pass


class View(Control):
    def __init__(self, route=None, controls=None, appbar=None, bgcolor=None, padding=None,
                 vertical_alignment=None, horizontal_alignment=None, scroll=None, **kw):
        Control.__init__(self, route=route, controls=list(controls or []), appbar=appbar, bgcolor=bgcolor,
                         padding=padding, **kw)


class Page(Column):
    """Page は窓(Android の画面)そのもの。中は Column と同じく上から並ぶ。"""
    _type = "window"

    def __init__(self):
        Column.__init__(self, [], spacing=10)
        self.__dict__["_page_init"] = True
        self.title = os.path.splitext(os.path.basename(os.environ.get("AIRSKY_SCRIPT", "Flet")))[0] or "Flet"
        self.bgcolor = None
        self.padding = 10
        self.theme_mode = None
        self.theme = None
        self.dark_theme = None
        self.appbar = None
        self.floating_action_button = None
        self.navigation_bar = None
        self.overlay = []
        self.dialog = None
        self.snack_bar = None
        self.banner = None
        self.route = "/"
        self.views = []
        self.fonts = {}
        self.on_keyboard_event = None
        self.on_resized = None
        self.on_resize = None
        self.on_close = None
        self.on_disconnect = None
        self.on_route_change = None
        self.on_view_pop = None
        self.client_storage = _ClientStorage()
        self.shared_preferences = self.client_storage
        self.session = _Session()
        self.window = _Window(self)
        self.platform = "android"
        self.web = False
        self.pwa = False
        self.width = None
        self.height = None
        self.__dict__["_dialogs"] = []
        self.__dict__["page"] = self
        self.__dict__["_closed"] = False

    def __setattr__(self, name, value):
        if name in ("window_width", "window_height", "window_resizable", "window_min_width",
                    "window_min_height", "window_max_width", "window_max_height", "window_left",
                    "window_top", "window_always_on_top", "window_bgcolor", "window_title_bar_hidden",
                    "window_frameless", "window_full_screen", "window_maximized", "window_center"):
            return
        Column.__setattr__(self, name, value)

    @property
    def window_width(self):
        return self.width

    @property
    def window_height(self):
        return self.height

    def _props(self):
        p = Column._props(self)
        p.pop("width", None)
        p.pop("height", None)
        title = self.title
        if isinstance(self.appbar, AppBar) and self.appbar.title is not None:
            t = self.appbar.title
            title = t.value if isinstance(t, Text) else str(t)
        p["title"] = str(title or "")
        bg = _g.color(self.bgcolor)
        if bg:
            p["bg"] = bg
        pad = _pad(self.padding)
        if pad:
            p["padding"] = pad
        p["conf"] = True
        if str(self.theme_mode or "") == "dark":
            p["dark"] = True
        elif str(self.theme_mode or "") == "light":
            p["dark"] = False
        seed = getattr(self.theme, "color_scheme_seed", None) if self.theme else None
        if seed:
            p["accent"] = _g.color(seed)
        actions = []
        if isinstance(self.appbar, AppBar):
            for i, a in enumerate(self.appbar.actions or []):
                label = getattr(a, "tooltip", None) or (getattr(a, "text", None)) or _glyph(getattr(a, "icon", None))
                actions.append({"type": "command", "label": str(label or "操作"), "key": "appbar:%d" % i,
                                "enabled": True})
        p["menu"] = actions
        return p

    def _children(self):
        out = list(self.controls or [])
        if isinstance(self.navigation_bar, Control):
            out.append(self.navigation_bar)
        if isinstance(self.floating_action_button, Control):
            out.append(self.floating_action_button)
        return out

    def _child_opts(self, child, index):
        if child is self.floating_action_button:
            return {"m": "box", "weight": 0, "expand": False, "align": "end"}
        return Column._child_opts(self, child, index)

    def _overlay_controls(self):
        out = [c for c in (self.overlay or []) if isinstance(c, (AlertDialog,))]
        if isinstance(self.dialog, AlertDialog):
            out.append(self.dialog)
        out += [d for d in self._dialogs if d not in out]
        return out

    def _refresh(self, page):
        Column._refresh(self, page)
        for d in self._overlay_controls():
            if d._id is None:
                d.__dict__["_parent"] = self
                d._mount(None, self)
            else:
                d._refresh(self)
        for sb in [self.snack_bar] + [c for c in (self.overlay or []) if isinstance(c, SnackBar)]:
            if isinstance(sb, SnackBar) and sb.open:
                sb._show()

    def update(self, *controls):
        with _lock:
            if self._id is None:
                return
            self._refresh(self)
            _g.bridge().flush()

    async def update_async(self, *controls):
        self.update()

    def add(self, *controls):
        self.controls.extend(controls)
        self.update()

    async def add_async(self, *controls):
        self.add(*controls)

    def insert(self, at, *controls):
        for i, c in enumerate(controls):
            self.controls.insert(at + i, c)
        self.update()

    def remove(self, *controls):
        for c in controls:
            if c in self.controls:
                self.controls.remove(c)
        self.update()

    def remove_at(self, index):
        self.controls.pop(index)
        self.update()

    def clean(self):
        self.controls.clear()
        self.update()

    def open(self, control):
        if isinstance(control, SnackBar):
            control.open = True
            control._show()
            return
        control.open = True
        if isinstance(control, AlertDialog) and control not in self._dialogs:
            self._dialogs.append(control)
        if isinstance(control, FilePicker):
            return
        self.update()

    def close(self, control):
        control.open = False
        self.update()

    def show_dialog(self, dialog):
        self.open(dialog)

    def pop_dialog(self):
        for d in reversed(self._dialogs):
            if d.open:
                self.close(d)
                return d

    def close_dialog(self):
        self.pop_dialog()
        if isinstance(self.dialog, AlertDialog):
            self.close(self.dialog)

    def show_snack_bar(self, snack_bar):
        snack_bar._show()

    def show_banner(self, banner):
        pass

    def close_banner(self):
        pass

    def launch_url(self, url, **kw):
        _g.bridge().send({"op": "open_url", "url": str(url)})

    async def launch_url_async(self, url, **kw):
        self.launch_url(url)

    def set_clipboard(self, value):
        _g.bridge().send({"op": "clipboard", "text": str(value)})

    async def set_clipboard_async(self, value):
        self.set_clipboard(value)

    def get_clipboard(self):
        return _g.bridge().request({"op": "clipboard_get"})

    async def get_clipboard_async(self):
        return self.get_clipboard()

    def go(self, route, **kw):
        self.route = route
        if self.on_route_change:
            ev = ControlEvent(name="route_change", data=route, page=self)
            ev.route = route
            _call_handler(self.on_route_change, ev, self)

    def run_task(self, handler, *args, **kwargs):
        return asyncio.run_coroutine_threadsafe(handler(*args, **kwargs), _async_loop())

    def run_thread(self, handler, *args):
        threading.Thread(target=handler, args=args, daemon=True).start()

    def window_destroy(self):
        self.__dict__["_closed"] = True
        _g.bridge().send({"op": "destroy", "id": self._id})
        _g.bridge().quit_all()

    window_close = window_destroy

    def scroll_to(self, *a, **kw):
        pass

    def set_focus(self, control):
        control.focus()

    def get_upload_url(self, file_name, expires):
        return ""

    def logout(self):
        pass

    def login(self, *a, **kw):
        pass

    def _on_android(self, ev):
        kind = ev.get("ev")
        if kind == "configure":
            self.__dict__["width"] = ev.get("w")
            self.__dict__["height"] = ev.get("h")
            self.window.__dict__["width"] = self.width
            self.window.__dict__["height"] = self.height
            handler = self.on_resized or self.on_resize
            if handler:
                _call_handler(handler, WindowResizeEvent(self.width, self.height, self), self)
        elif kind == "key":
            if ev.get("type", "press") == "press" and self.on_keyboard_event:
                key = _FLET_KEYS.get(ev.get("keysym"), (ev.get("char") or ev.get("keysym") or "").upper())
                mods = ev.get("mods") or ()
                _call_handler(self.on_keyboard_event,
                              KeyboardEvent(key, "Shift" in mods, "Control" in mods, "Alt" in mods, False, self), self)
        elif kind == "menu":
            key = str(ev.get("key", ""))
            if key.startswith("appbar:") and isinstance(self.appbar, AppBar):
                i = int(key.split(":")[1])
                acts = self.appbar.actions or []
                if 0 <= i < len(acts):
                    acts[i]._on_android({"ev": "command"})
        elif kind == "close":
            if self.on_close or self.on_disconnect:
                _call_handler(self.on_close or self.on_disconnect, ControlEvent(name="close", page=self), self)
            self.window_destroy()


_FLET_KEYS = {"Return": "Enter", "space": " ", "BackSpace": "Backspace", "Escape": "Escape",
              "Left": "Arrow Left", "Right": "Arrow Right", "Up": "Arrow Up", "Down": "Arrow Down",
              "Tab": "Tab", "Delete": "Delete", "Home": "Home", "End": "End"}


# ===================================================================== 始める

def app(target=None, name="", host=None, port=0, view=AppView.FLET_APP, assets_dir="assets",
        upload_dir=None, web_renderer=None, use_color_emoji=False, route_url_strategy=None,
        export_asgi_app=False, **kw):
    if target is None:
        target = kw.get("main")
    if assets_dir and not os.path.isabs(assets_dir):
        globals()["_assets"] = os.path.join(os.getcwd(), assets_dir)
    page = Page()
    b = _g.bridge()
    with _lock:
        page._mount(None, page)
    b.flush()
    if inspect.iscoroutinefunction(target):
        fut = asyncio.run_coroutine_threadsafe(target(page), _async_loop())

        def done(f):
            try:
                f.result()
            except Exception:
                import traceback
                traceback.print_exc()
            page.update()
        fut.add_done_callback(done)
    else:
        with _lock:
            b._call(target, page)
            page.update()
    b.mainloop(stop=lambda: page._closed)


def run(main=None, before_main=None, **kw):
    if before_main:
        kw.pop("before_main", None)
    return app(target=main, **kw)


async def app_async(target=None, **kw):
    app(target, **kw)


class UserControl(Control):
    """古い書き方の UserControl。build() が返したコントロールを中に表示する。"""
    _type = "frame"

    def __init__(self, **kw):
        Control.__init__(self, **kw)
        self.__dict__["_built"] = None

    def build(self):
        return None

    def _props(self):
        p = Control._props(self)
        p["orient"] = "v"
        return p

    def _children(self):
        if self._built is None:
            self.__dict__["_built"] = self.build()
        b = self._built
        if isinstance(b, list):
            return b
        return [b] if isinstance(b, Control) else []

    def _child_opts(self, child, index):
        return {"m": "box", "weight": child._expand_weight(), "expand": True}

    def did_mount(self):
        pass

    def will_unmount(self):
        pass


def __getattr__(name):
    # まだ対応していないコントロールは、中身を縦に並べるだけの箱にする
    if name[:1].isupper():
        class _Generic(Container):
            def __init__(self, *a, **kw):
                content = kw.pop("content", None)
                controls = kw.pop("controls", None)
                Container.__init__(self, content=content or (Column(controls) if controls else None))
                for k, v in kw.items():
                    setattr(self, k, v)
        _Generic.__name__ = name
        globals()[name] = _Generic
        return _Generic
    raise AttributeError(name)
