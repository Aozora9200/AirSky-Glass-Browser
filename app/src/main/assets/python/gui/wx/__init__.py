"""AirSky Browser の wxPython 互換モジュール。

wx.App / Frame / Panel / Dialog と主な部品・Sizer・イベント・メニュー・タイマー・描画(PaintDC)を、
Android の部品(Pixel 風)で表示する。座標と大きさの 1 ピクセルは Android の 1dp として扱う。
"""

import itertools
import os
import sys
import time

import _airsky_gui as _g

VERSION = (4, 2, 2, "", "")
VERSION_STRING = "4.2.2"
PlatformInfo = ("__WXGTK__", "wxGTK", "unicode", "airsky")
Platform = "__WXGTK__"


def version():
    return "4.2.2 airsky (phoenix)"


# ===================================================================== 定数

ID_ANY = -1
ID_NONE = -3
ID_SEPARATOR = -2
ID_OPEN, ID_CLOSE, ID_NEW, ID_SAVE, ID_SAVEAS, ID_REVERT, ID_EXIT = 5000, 5001, 5002, 5003, 5004, 5005, 5006
ID_UNDO, ID_REDO, ID_HELP, ID_PRINT, ID_PRINT_SETUP, ID_PREVIEW, ID_ABOUT = 5007, 5008, 5009, 5010, 5011, 5013, 5014
ID_DELETE, ID_FIND, ID_REPLACE, ID_PREFERENCES, ID_REFRESH = 5012, 5015, 5016, 5022, 5019
ID_CUT, ID_COPY, ID_PASTE, ID_CLEAR, ID_SELECTALL = 5031, 5032, 5033, 5034, 5037
ID_OK, ID_CANCEL, ID_APPLY, ID_YES, ID_NO, ID_STATIC, ID_FORWARD, ID_BACKWARD = 5100, 5101, 5102, 5103, 5104, 5105, 5106, 5107
ID_DEFAULT, ID_MORE, ID_SETUP, ID_RESET, ID_ADD, ID_REMOVE, ID_HOME = 5108, 5109, 5110, 5111, 5118, 5119, 5113
ID_HIGHEST = 5999

_STOCK_LABELS = {ID_OK: "OK", ID_CANCEL: "キャンセル", ID_YES: "はい", ID_NO: "いいえ", ID_APPLY: "適用",
                 ID_EXIT: "終了", ID_ABOUT: "情報", ID_OPEN: "開く", ID_SAVE: "保存", ID_SAVEAS: "名前を付けて保存",
                 ID_CLOSE: "閉じる", ID_NEW: "新規", ID_HELP: "ヘルプ", ID_COPY: "コピー", ID_PASTE: "貼り付け",
                 ID_CUT: "切り取り", ID_DELETE: "削除", ID_UNDO: "元に戻す", ID_REDO: "やり直し",
                 ID_PREFERENCES: "設定", ID_FIND: "検索", ID_SELECTALL: "すべて選択", ID_REFRESH: "更新",
                 ID_ADD: "追加", ID_REMOVE: "削除", ID_CLEAR: "クリア", ID_HOME: "ホーム"}

OK = 0x4
YES = 0x2
NO = 0x8
YES_NO = YES | NO
CANCEL = 0x10
APPLY = 0x20
CLOSE = 0x40
OK_DEFAULT = 0
YES_DEFAULT = 0
NO_DEFAULT = 0x80
CANCEL_DEFAULT = 0x80000000
HELP = 0x1000
ICON_EXCLAMATION = ICON_WARNING = 0x100
ICON_HAND = ICON_ERROR = ICON_STOP = 0x200
ICON_QUESTION = 0x400
ICON_INFORMATION = ICON_ASTERISK = 0x800
ICON_NONE = 0x40000
CENTRE = CENTER = 0x1
STAY_ON_TOP = 0x8000

HORIZONTAL = 0x4
VERTICAL = 0x8
BOTH = VERTICAL | HORIZONTAL
LEFT = 0x10
RIGHT = 0x20
UP = TOP = 0x40
DOWN = BOTTOM = 0x80
ALL = LEFT | RIGHT | TOP | BOTTOM
EXPAND = GROW = 0x2000
SHAPED = 0x4000
FIXED_MINSIZE = 0x8000
RESERVE_SPACE_EVEN_IF_HIDDEN = 0x0002
ALIGN_NOT = 0
ALIGN_LEFT = ALIGN_TOP = 0
ALIGN_CENTER_HORIZONTAL = ALIGN_CENTRE_HORIZONTAL = 0x0100
ALIGN_RIGHT = 0x0200
ALIGN_BOTTOM = 0x0400
ALIGN_CENTER_VERTICAL = ALIGN_CENTRE_VERTICAL = 0x0800
ALIGN_CENTER = ALIGN_CENTRE = 0x0900
ALIGN_MASK = 0x0F00

DEFAULT_FRAME_STYLE = 0x20400E40
DEFAULT_DIALOG_STYLE = 0x20001840
CAPTION = 0x20000000
SYSTEM_MENU = 0x0800
CLOSE_BOX = 0x1000
MINIMIZE_BOX = 0x0400
MAXIMIZE_BOX = 0x0200
RESIZE_BORDER = 0x0040
FRAME_FLOAT_ON_PARENT = 0x0008
FRAME_TOOL_WINDOW = 0x0004
FRAME_NO_TASKBAR = 0x0002
TAB_TRAVERSAL = 0x00080000
NO_BORDER = BORDER_NONE = 0x00200000
BORDER_SIMPLE = SIMPLE_BORDER = 0x02000000
BORDER_SUNKEN = SUNKEN_BORDER = 0x08000000
BORDER_RAISED = RAISED_BORDER = 0x04000000
BORDER_STATIC = STATIC_BORDER = 0x01000000
BORDER_THEME = BORDER_DEFAULT = 0
WANTS_CHARS = 0x00040000
FULL_REPAINT_ON_RESIZE = 0x00010000
CLIP_CHILDREN = 0x00400000
VSCROLL = 0x80000000
HSCROLL = 0x40000000

TE_MULTILINE = 0x0020
TE_PROCESS_TAB = 0x0040
TE_PROCESS_ENTER = 0x0400
TE_PASSWORD = 0x0800
TE_READONLY = 0x0010
TE_RICH = 0x0080
TE_RICH2 = 0x8000
TE_NO_VSCROLL = 0x0002
TE_AUTO_URL = 0x1000
TE_NOHIDESEL = 0x2000
TE_DONTWRAP = HSCROLL
TE_WORDWRAP = 0x0001
TE_CHARWRAP = 0x4000
TE_LEFT = 0
TE_CENTER = TE_CENTRE = 0x0100
TE_RIGHT = 0x0200
ST_NO_AUTORESIZE = 0x0001
ST_ELLIPSIZE_END = 0x0010
CB_SIMPLE = 0x0004
CB_SORT = 0x0008
CB_READONLY = 0x0010
CB_DROPDOWN = 0x0020
LB_SINGLE = 0
LB_MULTIPLE = 0x0040
LB_EXTENDED = 0x0080
LB_SORT = 0x0010
LB_HSCROLL = HSCROLL
LB_ALWAYS_SB = 0x0200
LB_NEEDED_SB = 0
RB_GROUP = 0x0004
RB_SINGLE = 0x0008
RA_SPECIFY_COLS = 0x0004
RA_SPECIFY_ROWS = 0x0008
SL_HORIZONTAL = HORIZONTAL
SL_VERTICAL = VERTICAL
SL_LABELS = 0x0010
SL_VALUE_LABEL = 0x0080
SL_MIN_MAX_LABELS = 0x0100
SL_AUTOTICKS = 0x0020
SP_ARROW_KEYS = 0x1000
SP_WRAP = 0x2000
GA_HORIZONTAL = HORIZONTAL
GA_VERTICAL = VERTICAL
GA_SMOOTH = 0x0020
LI_HORIZONTAL = HORIZONTAL
LI_VERTICAL = VERTICAL
BU_LEFT = 0x0040
BU_TOP = 0x0080
BU_RIGHT = 0x0100
BU_BOTTOM = 0x0200
BU_EXACTFIT = 0x0001
BU_NOTEXT = 0x0002
LC_LIST = 0x0010
LC_REPORT = 0x0020
LC_ICON = 0x0004
LC_SMALL_ICON = 0x0008
LC_SINGLE_SEL = 0x0040
LC_HRULES = 0x0400
LC_VRULES = 0x0800
LC_NO_HEADER = 0x0080
LC_EDIT_LABELS = 0x0100
LIST_FORMAT_LEFT = 0
LIST_FORMAT_RIGHT = 1
LIST_FORMAT_CENTRE = LIST_FORMAT_CENTER = 2
LIST_AUTOSIZE = -1
LIST_AUTOSIZE_USEHEADER = -2
LIST_NEXT_ALL = 0
LIST_STATE_SELECTED = 0x0004
LIST_STATE_FOCUSED = 0x0002
NB_TOP = 0
NB_BOTTOM = 0x0020
NB_LEFT = 0x0010
NB_RIGHT = 0x0040
NOT_FOUND = -1
FD_OPEN = 0x0001
FD_SAVE = 0x0002
FD_OVERWRITE_PROMPT = 0x0004
FD_NO_FOLLOW = 0x0008
FD_FILE_MUST_EXIST = 0x0010
FD_CHANGE_DIR = 0x0080
FD_PREVIEW = 0x0100
FD_MULTIPLE = 0x0200
FD_DEFAULT_STYLE = FD_OPEN
DD_DEFAULT_STYLE = DEFAULT_DIALOG_STYLE
DD_DIR_MUST_EXIST = 0x0200
CHOICEDLG_STYLE = DEFAULT_DIALOG_STYLE | OK | CANCEL | CENTRE
TEXTENTRY_DIALOG_STYLE = OK | CANCEL | CENTRE
ITEM_NORMAL = 0
ITEM_CHECK = 1
ITEM_RADIO = 2
ITEM_SEPARATOR = -1
ITEM_DROPDOWN = 3
EXEC_ASYNC = 0
SIZE_AUTO = 3
SIZE_USE_EXISTING = 0
SHOW_EFFECT_NONE = 0

FONTFAMILY_DEFAULT = DEFAULT = 70
FONTFAMILY_DECORATIVE = DECORATIVE = 71
FONTFAMILY_ROMAN = ROMAN = 72
FONTFAMILY_SCRIPT = SCRIPT = 73
FONTFAMILY_SWISS = SWISS = 74
FONTFAMILY_MODERN = MODERN = 75
FONTFAMILY_TELETYPE = TELETYPE = 76
FONTSTYLE_NORMAL = NORMAL = 90
FONTSTYLE_SLANT = SLANT = 94
FONTSTYLE_ITALIC = ITALIC = 93
FONTWEIGHT_NORMAL = 400
FONTWEIGHT_LIGHT = LIGHT = 300
FONTWEIGHT_BOLD = BOLD = 700
FONTWEIGHT_SEMIBOLD = 600
FONTWEIGHT_EXTRABOLD = 800
FONTWEIGHT_THIN = 100
FONTWEIGHT_MEDIUM = 500
FONTWEIGHT_HEAVY = 900

PENSTYLE_SOLID = SOLID = 100
PENSTYLE_DOT = DOT = 101
PENSTYLE_LONG_DASH = LONG_DASH = 102
PENSTYLE_SHORT_DASH = SHORT_DASH = 103
PENSTYLE_DOT_DASH = DOT_DASH = 104
PENSTYLE_TRANSPARENT = TRANSPARENT = 106
BRUSHSTYLE_SOLID = 100
BRUSHSTYLE_TRANSPARENT = 106
CAP_ROUND, CAP_PROJECTING, CAP_BUTT = 130, 131, 132
JOIN_BEVEL, JOIN_MITER, JOIN_ROUND = 120, 121, 122

WXK_BACK = 8
WXK_TAB = 9
WXK_RETURN = 13
WXK_ESCAPE = 27
WXK_SPACE = 32
WXK_DELETE = 127
WXK_START = 300
WXK_SHIFT = 306
WXK_ALT = 307
WXK_CONTROL = 308
WXK_RAW_CONTROL = 308
WXK_END = 312
WXK_HOME = 313
WXK_LEFT = 314
WXK_UP = 315
WXK_RIGHT = 316
WXK_DOWN = 317
WXK_PAGEUP = WXK_PRIOR = 366
WXK_PAGEDOWN = WXK_NEXT = 367
WXK_F1 = 340
WXK_F2, WXK_F3, WXK_F4, WXK_F5, WXK_F6 = 341, 342, 343, 344, 345
WXK_F7, WXK_F8, WXK_F9, WXK_F10, WXK_F11, WXK_F12 = 346, 347, 348, 349, 350, 351
WXK_NUMPAD_ENTER = 370
WXK_NONE = 0
MOD_NONE = 0
MOD_ALT = 1
MOD_CONTROL = MOD_CMD = MOD_RAW_CONTROL = 2
MOD_SHIFT = 4
ACCEL_NORMAL = 0
ACCEL_ALT = 1
ACCEL_CTRL = 2
ACCEL_SHIFT = 4

SYS_COLOUR_WINDOW = 5
SYS_COLOUR_WINDOWTEXT = 8
SYS_COLOUR_BTNFACE = SYS_COLOUR_3DFACE = 15
SYS_COLOUR_BTNTEXT = 18
SYS_COLOUR_HIGHLIGHT = 13
SYS_COLOUR_HIGHLIGHTTEXT = 14
SYS_COLOUR_GRAYTEXT = 17
SYS_COLOUR_BACKGROUND = 1
SYS_DEFAULT_GUI_FONT = 17
SYS_SCREEN_X = 19
SYS_SCREEN_Y = 20

BITMAP_TYPE_ANY = 50
BITMAP_TYPE_PNG = 15
BITMAP_TYPE_JPEG = 17
BITMAP_TYPE_BMP = 1
BITMAP_TYPE_GIF = 13
ART_INFORMATION = "wxART_INFORMATION"
ART_WARNING = "wxART_WARNING"
ART_ERROR = "wxART_ERROR"
ART_QUESTION = "wxART_QUESTION"
ART_FILE_OPEN = "wxART_FILE_OPEN"
ART_FILE_SAVE = "wxART_FILE_SAVE"
ART_TOOLBAR = "wxART_TOOLBAR_C"
ART_OTHER = "wxART_OTHER_C"
ART_MENU = "wxART_MENU_C"
ART_FOLDER = "wxART_FOLDER"
ART_NORMAL_FILE = "wxART_NORMAL_FILE"
ART_NEW = "wxART_NEW"
ART_QUIT = "wxART_QUIT"
ART_HELP = "wxART_HELP"

_ids = itertools.count(10000)


def NewIdRef(count=1):
    if count == 1:
        return next(_ids)
    return [next(_ids) for _ in range(count)]


NewId = NewIdRef


def Window_NewControlId(count=1):
    return NewIdRef(count)


# ===================================================================== 値の型

class Size(tuple):
    def __new__(cls, width=-1, height=-1):
        if isinstance(width, (tuple, list)):
            width, height = width[0], width[1]
        return tuple.__new__(cls, (int(width), int(height)))

    width = property(lambda s: s[0])
    height = property(lambda s: s[1])
    x = width
    y = height

    def GetWidth(self):
        return self[0]

    def GetHeight(self):
        return self[1]

    def Get(self):
        return (self[0], self[1])


class Point(tuple):
    def __new__(cls, x=0, y=0):
        if isinstance(x, (tuple, list)):
            x, y = x[0], x[1]
        return tuple.__new__(cls, (int(x), int(y)))

    x = property(lambda s: s[0])
    y = property(lambda s: s[1])

    def Get(self):
        return (self[0], self[1])

    def __add__(self, o):
        return Point(self[0] + o[0], self[1] + o[1])

    def __sub__(self, o):
        return Point(self[0] - o[0], self[1] - o[1])


class RealPoint(Point):
    pass


class Rect:
    def __init__(self, x=0, y=0, width=0, height=0):
        if isinstance(x, (tuple, list)) and len(x) == 4:
            x, y, width, height = x
        self.x, self.y, self.width, self.height = x, y, width, height

    def Get(self):
        return (self.x, self.y, self.width, self.height)

    def Contains(self, pt):
        return self.x <= pt[0] < self.x + self.width and self.y <= pt[1] < self.y + self.height

    def Intersects(self, r):
        return not (r.x >= self.x + self.width or r.x + r.width <= self.x or
                    r.y >= self.y + self.height or r.y + r.height <= self.y)

    GetX = lambda s: s.x
    GetY = lambda s: s.y
    GetWidth = lambda s: s.width
    GetHeight = lambda s: s.height
    GetSize = lambda s: Size(s.width, s.height)
    GetPosition = lambda s: Point(s.x, s.y)


DefaultPosition = Point(-1, -1)
DefaultSize = Size(-1, -1)
DefaultCoord = -1
EmptyString = ""


class Colour:
    def __init__(self, red=0, green=0, blue=0, alpha=255):
        if isinstance(red, Colour):
            red, green, blue, alpha = red.red, red.green, red.blue, red.alpha
        elif isinstance(red, str):
            c = _g.color(red) or "#000000"
            h = c[1:]
            if len(h) == 8:
                alpha, h = int(h[0:2], 16), h[2:]
            red, green, blue = int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)
        elif isinstance(red, (tuple, list)):
            vals = list(red) + [255]
            red, green, blue, alpha = vals[0], vals[1], vals[2], vals[3] if len(red) > 3 else 255
        self.red, self.green, self.blue, self.alpha = int(red), int(green), int(blue), int(alpha)

    def _airsky_color(self):
        if self.alpha < 255:
            return "#%02x%02x%02x%02x" % (self.alpha, self.red, self.green, self.blue)
        return "#%02x%02x%02x" % (self.red, self.green, self.blue)

    def Red(self):
        return self.red

    def Green(self):
        return self.green

    def Blue(self):
        return self.blue

    def Alpha(self):
        return self.alpha

    def Get(self, includeAlpha=True):
        return (self.red, self.green, self.blue, self.alpha) if includeAlpha else (self.red, self.green, self.blue)

    def GetAsString(self, flags=0):
        return "#%02X%02X%02X" % (self.red, self.green, self.blue)

    def IsOk(self):
        return True

    def __eq__(self, o):
        return isinstance(o, Colour) and self.Get() == o.Get()

    def __iter__(self):
        return iter(self.Get())

    def __repr__(self):
        return "wx.Colour(%d, %d, %d, %d)" % self.Get()


Color = Colour
BLACK = Colour(0, 0, 0)
WHITE = Colour(255, 255, 255)
RED = Colour(255, 0, 0)
GREEN = Colour(0, 255, 0)
BLUE = Colour(0, 0, 255)
CYAN = Colour(0, 255, 255)
YELLOW = Colour(255, 255, 0)
LIGHT_GREY = Colour(192, 192, 192)
NullColour = Colour(0, 0, 0, 0)
TransparentColour = Colour(0, 0, 0, 0)


def _col(c):
    if c is None:
        return None
    return _g.color(c)


class Font:
    def __init__(self, pointSize=10, family=FONTFAMILY_DEFAULT, style=FONTSTYLE_NORMAL,
                 weight=FONTWEIGHT_NORMAL, underline=False, faceName="", encoding=0):
        if isinstance(pointSize, FontInfo):
            fi = pointSize
            pointSize, family, style, weight, underline, faceName = (fi.size, fi.family, fi.style,
                                                                   fi.weight, fi.underline, fi.face)
        elif isinstance(pointSize, (tuple, list)):
            pointSize = pointSize[1] if len(pointSize) > 1 else 10
        self.size = pointSize
        self.family = family
        self.style = style
        self.weight = weight
        self.underline = underline
        self.face = faceName

    def _airsky_font(self):
        fam = "mono" if self.family in (FONTFAMILY_MODERN, FONTFAMILY_TELETYPE) else \
            ("serif" if self.family == FONTFAMILY_ROMAN else "sans")
        if self.face:
            fam = _g._family(self.face)
        d = {"family": fam, "size": round(float(self.size) * 1.333, 1)}
        if self.weight in (FONTWEIGHT_BOLD, FONTWEIGHT_SEMIBOLD, FONTWEIGHT_EXTRABOLD, FONTWEIGHT_HEAVY):
            d["bold"] = True
        if self.style in (FONTSTYLE_ITALIC, FONTSTYLE_SLANT):
            d["italic"] = True
        if self.underline:
            d["underline"] = True
        return d

    def GetPointSize(self):
        return self.size

    def SetPointSize(self, s):
        self.size = s

    def GetWeight(self):
        return self.weight

    def SetWeight(self, w):
        self.weight = w

    def GetStyle(self):
        return self.style

    def SetStyle(self, s):
        self.style = s

    def GetFamily(self):
        return self.family

    def SetFamily(self, f):
        self.family = f

    def GetFaceName(self):
        return self.face

    def SetFaceName(self, f):
        self.face = f
        return True

    def SetUnderlined(self, u):
        self.underline = u

    def GetUnderlined(self):
        return self.underline

    def Bold(self):
        f = Font(self.size, self.family, self.style, FONTWEIGHT_BOLD, self.underline, self.face)
        return f

    def Italic(self):
        return Font(self.size, self.family, FONTSTYLE_ITALIC, self.weight, self.underline, self.face)

    def Larger(self):
        return Font(self.size * 1.2, self.family, self.style, self.weight, self.underline, self.face)

    def Smaller(self):
        return Font(self.size / 1.2, self.family, self.style, self.weight, self.underline, self.face)

    def Scaled(self, x):
        return Font(self.size * x, self.family, self.style, self.weight, self.underline, self.face)

    def MakeBold(self):
        self.weight = FONTWEIGHT_BOLD
        return self

    def MakeItalic(self):
        self.style = FONTSTYLE_ITALIC
        return self

    def MakeLarger(self):
        self.size *= 1.2
        return self

    def MakeSmaller(self):
        self.size /= 1.2
        return self

    def IsOk(self):
        return True


class FontInfo:
    def __init__(self, size=10):
        self.size, self.family, self.style, self.weight, self.underline, self.face = \
            size, FONTFAMILY_DEFAULT, FONTSTYLE_NORMAL, FONTWEIGHT_NORMAL, False, ""

    def Bold(self, b=True):
        self.weight = FONTWEIGHT_BOLD if b else FONTWEIGHT_NORMAL
        return self

    def Italic(self, b=True):
        self.style = FONTSTYLE_ITALIC if b else FONTSTYLE_NORMAL
        return self

    def Family(self, f):
        self.family = f
        return self

    def FaceName(self, f):
        self.face = f
        return self

    def Underlined(self, b=True):
        self.underline = b
        return self


NORMAL_FONT = Font(10)
SMALL_FONT = Font(8)
ITALIC_FONT = Font(10, style=FONTSTYLE_ITALIC)
SWISS_FONT = Font(10, FONTFAMILY_SWISS)
NullFont = Font(10)


class Pen:
    def __init__(self, colour=BLACK, width=1, style=PENSTYLE_SOLID):
        self.colour = Colour(colour) if not isinstance(colour, Colour) else colour
        self.width = width
        self.style = style

    def SetColour(self, c):
        self.colour = Colour(c)

    def GetColour(self):
        return self.colour

    def SetWidth(self, w):
        self.width = w

    def GetWidth(self):
        return self.width

    def SetStyle(self, s):
        self.style = s

    def SetCap(self, c):
        pass

    def SetJoin(self, j):
        pass

    def IsOk(self):
        return True


class Brush:
    def __init__(self, colour=BLACK, style=BRUSHSTYLE_SOLID):
        self.colour = Colour(colour) if not isinstance(colour, Colour) else colour
        self.style = style

    def SetColour(self, c):
        self.colour = Colour(c)

    def GetColour(self):
        return self.colour

    def SetStyle(self, s):
        self.style = s

    def IsOk(self):
        return True


BLACK_PEN = Pen(BLACK)
WHITE_PEN = Pen(WHITE)
RED_PEN = Pen(RED)
GREEN_PEN = Pen(GREEN)
BLUE_PEN = Pen(BLUE)
TRANSPARENT_PEN = Pen(BLACK, 1, PENSTYLE_TRANSPARENT)
NullPen = TRANSPARENT_PEN
BLACK_BRUSH = Brush(BLACK)
WHITE_BRUSH = Brush(WHITE)
RED_BRUSH = Brush(RED)
GREEN_BRUSH = Brush(GREEN)
BLUE_BRUSH = Brush(BLUE)
TRANSPARENT_BRUSH = Brush(BLACK, BRUSHSTYLE_TRANSPARENT)
NullBrush = TRANSPARENT_BRUSH
LIGHT_GREY_BRUSH = Brush(LIGHT_GREY)


class Bitmap:
    def __init__(self, name=None, type=BITMAP_TYPE_ANY, width=None, height=None):
        if isinstance(name, Image):
            self._path = name._path
        elif isinstance(name, (int, float)):
            self._path = None
        else:
            self._path = name
        self._scale = 1.0

    @staticmethod
    def FromRGBA(width, height, red=0, green=0, blue=0, alpha=0):
        return Bitmap()

    def _ref(self):
        r = _g.image_ref(self._path) if self._path else None
        if r and self._scale != 1.0:
            r["scale"] = self._scale
        return r

    def IsOk(self):
        return bool(self._path) and os.path.exists(str(self._path))

    def GetSize(self):
        return Size(0, 0)

    def GetWidth(self):
        return 0

    def GetHeight(self):
        return 0

    def ConvertToImage(self):
        return Image(self._path)



class Image:
    def __init__(self, name=None, type=BITMAP_TYPE_ANY, index=-1):
        self._path = name if isinstance(name, str) else None
        self._scale = 1.0

    def Scale(self, width, height, quality=0):
        return self

    Rescale = Scale

    def ConvertToBitmap(self, depth=-1):
        b = Bitmap(self._path)
        return b

    def IsOk(self):
        return bool(self._path)

    def GetWidth(self):
        return 0

    def GetHeight(self):
        return 0


NullBitmap = Bitmap()


class ArtProvider:
    @staticmethod
    def GetBitmap(id, client=ART_OTHER, size=DefaultSize):
        return NullBitmap

    @staticmethod
    def GetIcon(id, client=ART_OTHER, size=DefaultSize):
        return NullBitmap


class Icon(Bitmap):
    def CopyFromBitmap(self, bmp):
        self._path = bmp._path


class SystemSettings:
    @staticmethod
    def GetColour(index):
        return {SYS_COLOUR_WINDOW: WHITE, SYS_COLOUR_WINDOWTEXT: BLACK, SYS_COLOUR_BTNFACE: Colour(240, 240, 240),
                SYS_COLOUR_HIGHLIGHT: Colour(11, 87, 208), SYS_COLOUR_HIGHLIGHTTEXT: WHITE,
                SYS_COLOUR_GRAYTEXT: Colour(141, 141, 141), SYS_COLOUR_BTNTEXT: BLACK}.get(index, WHITE)

    @staticmethod
    def GetFont(index):
        return Font(10)

    @staticmethod
    def GetMetric(index, win=None):
        if index == SYS_SCREEN_X:
            return int(os.environ.get("AIRSKY_SCREEN_W", "400"))
        if index == SYS_SCREEN_Y:
            return int(os.environ.get("AIRSKY_SCREEN_H", "800"))
        return -1


def GetDisplaySize():
    return Size(int(os.environ.get("AIRSKY_SCREEN_W", "400")), int(os.environ.get("AIRSKY_SCREEN_H", "800")))


def DisplaySize():
    return GetDisplaySize()


class Display:
    def __init__(self, index=0):
        pass

    def GetGeometry(self):
        s = GetDisplaySize()
        return Rect(0, 0, s[0], s[1])

    GetClientArea = GetGeometry

    @staticmethod
    def GetCount():
        return 1


# ===================================================================== イベント

class PyEventBinder:
    def __init__(self, evtType, expectedIDs=0):
        self.evtType = evtType if isinstance(evtType, (list, tuple)) else [evtType]
        self.typeId = self.evtType[0]

    def Bind(self, target, id1, id2, function):
        target.Bind(self, function, id=id1)

    def __call__(self, *args):
        # 古い書き方: wx.EVT_BUTTON(parent, id, handler)
        if len(args) == 3:
            args[0].Bind(self, args[2], id=args[1])
        elif len(args) == 2:
            args[0].Bind(self, args[1])


_etypes = itertools.count(10100)


def NewEventType():
    return next(_etypes)


def _binder(name):
    return PyEventBinder(name)


EVT_BUTTON = _binder("button")
EVT_TOGGLEBUTTON = _binder("toggle")
EVT_TEXT = _binder("text")
EVT_TEXT_ENTER = _binder("text_enter")
EVT_CHECKBOX = _binder("checkbox")
EVT_RADIOBUTTON = _binder("radiobutton")
EVT_RADIOBOX = _binder("radiobox")
EVT_CHOICE = _binder("choice")
EVT_COMBOBOX = _binder("combobox")
EVT_LISTBOX = _binder("listbox")
EVT_LISTBOX_DCLICK = _binder("listbox_dclick")
EVT_CHECKLISTBOX = _binder("checklistbox")
EVT_SLIDER = _binder("slider")
EVT_SCROLL = EVT_SCROLL_CHANGED = EVT_SCROLL_THUMBTRACK = _binder("slider")
EVT_SPINCTRL = _binder("spinctrl")
EVT_SPINCTRLDOUBLE = _binder("spinctrl")
EVT_SPIN = _binder("spinctrl")
EVT_MENU = _binder("menu")
EVT_TOOL = EVT_MENU
EVT_CLOSE = _binder("close")
EVT_SIZE = _binder("size")
EVT_MOVE = _binder("move")
EVT_PAINT = _binder("paint")
EVT_ERASE_BACKGROUND = _binder("erase")
EVT_TIMER = _binder("timer")
EVT_IDLE = _binder("idle")
EVT_SHOW = _binder("show")
EVT_ACTIVATE = _binder("activate")
EVT_LEFT_DOWN = _binder("left_down")
EVT_LEFT_UP = _binder("left_up")
EVT_LEFT_DCLICK = _binder("left_dclick")
EVT_RIGHT_DOWN = _binder("right_down")
EVT_RIGHT_UP = _binder("right_up")
EVT_MIDDLE_DOWN = _binder("middle_down")
EVT_MOTION = _binder("motion")
EVT_MOUSEWHEEL = _binder("wheel")
EVT_ENTER_WINDOW = _binder("enter")
EVT_LEAVE_WINDOW = _binder("leave")
EVT_MOUSE_EVENTS = _binder(["left_down", "left_up", "motion", "left_dclick", "right_down", "right_up", "wheel"])
EVT_KEY_DOWN = _binder("key_down")
EVT_KEY_UP = _binder("key_up")
EVT_CHAR = _binder("char")
EVT_CHAR_HOOK = _binder("char_hook")
EVT_SET_FOCUS = _binder("set_focus")
EVT_KILL_FOCUS = _binder("kill_focus")
EVT_NOTEBOOK_PAGE_CHANGED = _binder("page_changed")
EVT_NOTEBOOK_PAGE_CHANGING = _binder("page_changing")
EVT_LIST_ITEM_SELECTED = _binder("list_selected")
EVT_LIST_ITEM_ACTIVATED = _binder("list_activated")
EVT_LIST_ITEM_DESELECTED = _binder("list_deselected")
EVT_LIST_COL_CLICK = _binder("list_col_click")
EVT_WINDOW_DESTROY = _binder("destroy")
EVT_HYPERLINK = _binder("hyperlink")
EVT_CONTEXT_MENU = _binder("context_menu")
EVT_UPDATE_UI = _binder("update_ui")
EVT_COMMAND_BUTTON_CLICKED = EVT_BUTTON

_COMMAND_EVENTS = {"button", "toggle", "text", "text_enter", "checkbox", "radiobutton", "radiobox",
                   "choice", "combobox", "listbox", "listbox_dclick", "checklistbox", "slider",
                   "spinctrl", "menu", "page_changed", "page_changing", "list_selected",
                   "list_activated", "list_deselected", "list_col_click", "hyperlink"}

wxEVT_BUTTON = wxEVT_COMMAND_BUTTON_CLICKED = "button"
wxEVT_TEXT = "text"
wxEVT_CLOSE_WINDOW = "close"
wxEVT_TIMER = "timer"


class Event:
    def __init__(self, etype=None, obj=None, id=ID_ANY, **kw):
        self._type = etype
        self._obj = obj
        self._id = id if id != ID_ANY or obj is None else getattr(obj, "_wid", ID_ANY)
        self._skipped = False
        self._vetoed = False
        self.__dict__.update(kw)

    def Skip(self, skip=True):
        self._skipped = skip

    def GetSkipped(self):
        return self._skipped

    def GetEventObject(self):
        return self._obj

    def SetEventObject(self, o):
        self._obj = o

    def GetId(self):
        return self._id

    def SetId(self, i):
        self._id = i

    def GetEventType(self):
        return self._type

    def StopPropagation(self):
        self._skipped = False
        return 0

    def ResumePropagation(self, level):
        pass

    def ShouldPropagate(self):
        return True

    # コマンド
    def GetString(self):
        return self.__dict__.get("string", "")

    def GetInt(self):
        return self.__dict__.get("int", 0)

    def GetSelection(self):
        return self.__dict__.get("int", 0)

    def IsChecked(self):
        return bool(self.__dict__.get("int", 0))

    def IsSelection(self):
        return True

    def GetExtraLong(self):
        return 0

    def GetClientData(self):
        return None

    def GetIndex(self):
        return self.__dict__.get("int", -1)

    def GetItem(self):
        return self

    def GetText(self):
        return self.__dict__.get("string", "")

    def GetColumn(self):
        return self.__dict__.get("column", 0)

    def GetOldSelection(self):
        return self.__dict__.get("old", -1)

    def GetPosition(self):
        if "pos" in self.__dict__:
            return Point(self.pos)
        return Point(self.__dict__.get("x", 0), self.__dict__.get("y", 0))

    # 閉じる
    def CanVeto(self):
        return True

    def Veto(self, veto=True):
        self._vetoed = veto

    def GetVeto(self):
        return self._vetoed

    # マウス
    def GetX(self):
        return int(self.__dict__.get("x", 0))

    def GetY(self):
        return int(self.__dict__.get("y", 0))

    def GetLogicalPosition(self, dc=None):
        return self.GetPosition()

    def LeftDown(self):
        return self._type == "left_down"

    def LeftUp(self):
        return self._type == "left_up"

    def LeftDClick(self):
        return self._type == "left_dclick"

    def RightDown(self):
        return self._type == "right_down"

    def LeftIsDown(self):
        return bool(self.__dict__.get("down", False))

    def RightIsDown(self):
        return False

    def Dragging(self):
        return self._type == "motion" and self.LeftIsDown()

    def Moving(self):
        return self._type == "motion" and not self.LeftIsDown()

    def ButtonDown(self, but=-1):
        return self._type in ("left_down", "right_down")

    def ButtonUp(self, but=-1):
        return self._type in ("left_up", "right_up")

    def GetButton(self):
        return 1

    def GetWheelRotation(self):
        return int(self.__dict__.get("delta", 0))

    def GetWheelDelta(self):
        return 120

    def Entering(self):
        return False

    def Leaving(self):
        return False

    # キー
    def GetKeyCode(self):
        return self.__dict__.get("keycode", 0)

    def GetUnicodeKey(self):
        ch = self.__dict__.get("char", "")
        return ord(ch) if ch else 0

    def GetRawKeyCode(self):
        return self.GetKeyCode()

    def ControlDown(self):
        return "Control" in self.__dict__.get("mods", ())

    CmdDown = RawControlDown = ControlDown

    def ShiftDown(self):
        return "Shift" in self.__dict__.get("mods", ())

    def AltDown(self):
        return "Alt" in self.__dict__.get("mods", ())

    def MetaDown(self):
        return False

    def HasModifiers(self):
        return bool(self.__dict__.get("mods"))

    def GetModifiers(self):
        m = self.__dict__.get("mods", ())
        return (MOD_CONTROL if "Control" in m else 0) | (MOD_SHIFT if "Shift" in m else 0) | \
            (MOD_ALT if "Alt" in m else 0)

    # 大きさ
    def GetSize(self):
        return Size(self.__dict__.get("w", 0), self.__dict__.get("h", 0))

    def GetRect(self):
        return Rect(0, 0, self.__dict__.get("w", 0), self.__dict__.get("h", 0))

    def GetActive(self):
        return True

    def Clone(self):
        e = Event(self._type, self._obj, self._id)
        e.__dict__.update(self.__dict__)
        return e


CommandEvent = MouseEvent = KeyEvent = CloseEvent = SizeEvent = PaintEvent = TimerEvent = Event
NotifyEvent = ListEvent = BookCtrlEvent = NotebookEvent = SpinEvent = ScrollEvent = Event


class PyCommandEvent(Event):
    def __init__(self, eventType=None, id=0):
        Event.__init__(self, eventType, None, id)


class PyEvent(PyCommandEvent):
    pass


def PostEvent(dest, event):
    CallAfter(dest.ProcessEvent, event)


class EvtHandler:
    def _eh_init(self):
        self._handlers = []  # (etype, id, id2, handler, source)

    def Bind(self, event, handler, source=None, id=ID_ANY, id2=ID_ANY):
        if not hasattr(self, "_handlers"):
            self._eh_init()
        types = event.evtType if isinstance(event, PyEventBinder) else [event]
        if source is not None and id == ID_ANY:
            id = source.GetId() if hasattr(source, "GetId") else ID_ANY
        for t in types:
            self._handlers.append((t, id, id2, handler, source))
        if isinstance(self, Window):
            self._on_bound(types)

    def Unbind(self, event, source=None, id=ID_ANY, id2=ID_ANY, handler=None):
        types = event.evtType if isinstance(event, PyEventBinder) else [event]
        n = len(self._handlers)
        self._handlers = [h for h in self._handlers
                          if not (h[0] in types and (handler is None or h[3] == handler)
                                  and (source is None or h[4] is source))]
        return len(self._handlers) != n

    def _handle(self, ev):
        """自分の結び付けを呼ぶ。処理したら True(Skip されたら False)。"""
        for t, i, i2, fn, src in list(getattr(self, "_handlers", ())):
            if t != ev._type:
                continue
            if i not in (ID_ANY, -1) and i2 in (ID_ANY, -1) and ev._id != i:
                continue
            if i not in (ID_ANY, -1) and i2 not in (ID_ANY, -1) and not (i <= ev._id <= i2):
                continue
            if src is not None and ev._obj is not src and ev._id != getattr(src, "_wid", None):
                continue
            ev._skipped = False
            _g.bridge()._call(fn, ev)
            if not ev._skipped:
                return True
        return False

    def ProcessEvent(self, ev):
        return _emit(self, ev)

    def QueueEvent(self, ev):
        CallAfter(self.ProcessEvent, ev)

    AddPendingEvent = QueueEvent

    def SafelyProcessEvent(self, ev):
        return self.ProcessEvent(ev)


def _emit(target, ev):
    """コマンドイベントは親へ、親の窓へと伝わる。"""
    w = target
    while w is not None:
        if w._handle(ev):
            return True
        if ev._type not in _COMMAND_EVENTS:
            break
        if isinstance(w, TopLevelWindow):
            app = App._instance
            if app is not None and app._handle(ev):
                return True
            break
        w = getattr(w, "_parent", None)
    return False


# ===================================================================== アプリ

class PyApp(EvtHandler):
    pass


class App(EvtHandler):
    _instance = None

    def __init__(self, redirect=False, filename=None, useBestVisual=False, clearSigInt=True):
        self._eh_init()
        App._instance = self
        self._top = None
        self._exit = False
        self._exit_on_frame_delete = True
        _g.bridge()
        if hasattr(self, "OnPreInit"):
            self.OnPreInit()
        r = self.OnInit()
        if r is False:
            raise SystemExit(0)

    def OnInit(self):
        return True

    def OnExit(self):
        return 0

    def MainLoop(self):
        b = _g.bridge()
        b.mainloop(stop=lambda: self._exit or not any(not w._destroyed for w in _tops))
        self.OnExit()
        return 0

    def ExitMainLoop(self):
        self._exit = True
        _g.bridge().quit_all()

    def SetTopWindow(self, w):
        self._top = w

    def GetTopWindow(self):
        return self._top or (next((w for w in _tops if not w._destroyed), None))

    def SetExitOnFrameDelete(self, flag):
        self._exit_on_frame_delete = flag

    def Yield(self, onlyIfNeeded=False):
        _g.bridge().process_pending()
        return True

    SafeYield = Yield

    def GetAppName(self):
        return os.path.splitext(os.path.basename(sys.argv[0] or "wx"))[0]

    SetAppName = SetAppDisplayName = SetVendorName = lambda self, *a: None

    def IsActive(self):
        return True

    def Pending(self):
        return not _g.bridge().events.empty()

    def Dispatch(self):
        _g.bridge().process_pending()
        return True

    def ProcessPendingEvents(self):
        _g.bridge().process_pending()

    @staticmethod
    def IsMainLoopRunning():
        return _g.bridge().loop_depth > 0


PySimpleApp = App


def GetApp():
    return App._instance


def CallAfter(fn, *args, **kw):
    _g.bridge().call_later(0, lambda: fn(*args, **kw))


class CallLater:
    def __init__(self, millis, callableObj, *args, **kwargs):
        self._ms, self._fn, self._args, self._kw = millis, callableObj, args, kwargs
        self._tid = None
        self._running = False
        self._result = None
        self.Start()

    def _fire(self):
        self._running = False
        self._result = self._fn(*self._args, **self._kw)

    def Start(self, millis=None, *args, **kw):
        if millis is not None:
            self._ms = millis
        if args:
            self._args = args
        if kw:
            self._kw = kw
        self.Stop()
        self._running = True
        self._tid = _g.bridge().call_later(int(self._ms), self._fire)

    Restart = Start

    def Stop(self):
        if self._tid is not None:
            _g.bridge().cancel(self._tid)
        self._running = False

    def IsRunning(self):
        return self._running

    def HasRun(self):
        return not self._running and self._tid is not None

    def GetResult(self):
        return self._result

    SetArgs = lambda self, *a, **kw: setattr(self, "_args", a)


FutureCall = CallLater


def Yield():
    _g.bridge().process_pending()
    return True


SafeYield = YieldIfNeeded = Yield


def Bell():
    _g.bridge().send({"op": "bell"})


def Exit():
    if App._instance:
        App._instance.ExitMainLoop()
    raise SystemExit(0)


def Sleep(secs):
    time.sleep(secs)


def MilliSleep(ms):
    time.sleep(ms / 1000.0)


MicroSleep = lambda us: time.sleep(us / 1e6)


def GetMousePosition():
    return Point(0, 0)


def LogMessage(msg, *args):
    print(msg % args if args else msg)


LogError = LogWarning = LogStatus = LogVerbose = LogDebug = LogInfo = LogMessage


class Log:
    @staticmethod
    def SetActiveTarget(t):
        return None

    @staticmethod
    def EnableLogging(enable=True):
        return True


class LogNull:
    pass


def BeginBusyCursor(cursor=None):
    pass


def EndBusyCursor():
    pass


class BusyCursor:
    def __init__(self, *a):
        pass

    def __enter__(self):
        return self

    def __exit__(self, *a):
        return False


class BusyInfo(BusyCursor):
    pass


class Cursor:
    def __init__(self, *a):
        pass


CURSOR_ARROW = CURSOR_HAND = CURSOR_WAIT = CURSOR_CROSS = CURSOR_IBEAM = 0
StockCursor = Cursor


def LaunchDefaultBrowser(url, flags=0):
    _g.bridge().send({"op": "open_url", "url": str(url)})
    return True


class Timer(EvtHandler):
    def __init__(self, owner=None, id=ID_ANY):
        self._eh_init()
        self._owner = owner if owner is not None else self
        self._wid = id if id != ID_ANY else NewIdRef()
        self._tid = None
        self._ms = 0
        self._oneshot = False

    def Start(self, milliseconds=-1, oneShot=False):
        if milliseconds >= 0:
            self._ms = milliseconds
        self._oneshot = bool(oneShot)
        self.Stop()
        self._schedule()
        return True

    def StartOnce(self, milliseconds=-1):
        return self.Start(milliseconds, True)

    def _schedule(self):
        self._tid = _g.bridge().call_later(max(1, int(self._ms)), self._fire)

    def _fire(self):
        if self._tid is None:
            return
        if self._oneshot:
            self._tid = None
        else:
            self._schedule()
        self.Notify()

    def Notify(self):
        ev = Event("timer", self, self._wid)
        if not self._handle(ev) and self._owner is not self:
            self._owner._handle(ev)

    def Stop(self):
        if self._tid is not None:
            _g.bridge().cancel(self._tid)
        self._tid = None

    def IsRunning(self):
        return self._tid is not None

    def GetInterval(self):
        return self._ms

    def GetId(self):
        return self._wid

    def IsOneShot(self):
        return self._oneshot


class PyTimer(Timer):
    def __init__(self, notify):
        Timer.__init__(self)
        self._notify = notify

    def Notify(self):
        self._notify()


# ===================================================================== 窓の土台

_tops = []
_windows = {}


def _flag_margin(flag, border):
    b = float(border or 0)
    return [b if flag & LEFT else 0, b if flag & TOP else 0, b if flag & RIGHT else 0, b if flag & BOTTOM else 0]


class Window(EvtHandler):
    _type = "frame"
    _container = True

    def __init__(self, parent=None, id=ID_ANY, pos=DefaultPosition, size=DefaultSize, style=0,
                 name="", **kw):
        self._eh_init()
        self._parent = parent
        self._children = []
        self._wid = id if id not in (ID_ANY, -1, None) else NewIdRef()
        self._style = style or 0
        self._name = name
        self._id = _g.bridge().new_id()
        self._destroyed = False
        self._sizer = None
        self._containing_sizer = None
        self._size = (0, 0)
        self._req = (size[0] if size else -1, size[1] if size else -1)
        self._min = (-1, -1)
        self._pos = pos
        self._shown = True
        self._enabled = True
        self._label = kw.pop("label", "")
        self._fg = self._bg = self._font = None
        self._tooltip = None
        self._mouse = False
        self._draw = None
        self._layout_sent = None
        self._extra_init(**kw)
        if parent is not None:
            parent._children.append(self)
        _windows[self._id] = self
        _g.bridge().handlers[self._id] = self._on_android
        self._create()
        if parent is not None:
            parent._child_added(self)

    def _extra_init(self, **kw):
        pass

    def _create(self):
        msg = {"op": "new", "id": self._id, "type": self._type, "props": self._props()}
        if self._parent is not None:
            msg["parent"] = self._parent._content_id()
        _g.bridge().send(msg)

    def _content_id(self):
        return self._id

    def _props(self):
        p = {}
        if self._fg is not None:
            p["fg"] = _col(self._fg)
        if self._bg is not None:
            p["bg"] = _col(self._bg)
        if self._font is not None:
            p["font"] = _g.font(self._font)
        if self._req[0] > 0:
            p["width"] = self._req[0]
        if self._req[1] > 0:
            p["height"] = self._req[1]
        if self._min[0] > 0:
            p["minw"] = self._min[0]
        if self._min[1] > 0:
            p["minh"] = self._min[1]
        if not self._enabled:
            p["state"] = "disabled"
        if not self._shown:
            p["visible"] = False
        if self._style & (BORDER_SIMPLE | BORDER_SUNKEN | BORDER_RAISED | BORDER_STATIC):
            p["border"] = {"width": 1, "color": "#c4c7c5"}
        p["mouse"] = self._mouse
        return p

    def _send(self, **props):
        if not self._destroyed:
            _g.bridge().send({"op": "set", "id": self._id, "props": props})

    def _refresh_props(self):
        if not self._destroyed:
            _g.bridge().send({"op": "set", "id": self._id, "props": self._props()})

    def _child_added(self, child):
        """Sizer を使わない親の中では、位置(pos)に置く。子が 1 つだけの Frame では全体に広げる。"""
        if self._sizer is not None:
            return
        child._auto_layout()

    def _auto_layout(self):
        if self._containing_sizer is not None or isinstance(self, TopLevelWindow):
            return
        par = self._parent
        if par is None:
            return
        if isinstance(par, TopLevelWindow) and par._sizer is None:
            kids = [c for c in par._children if not c._destroyed and not isinstance(c, (TopLevelWindow, StatusBar))]
            if len(kids) == 1:
                self._layout({"m": "place", "x": 0, "y": 0, "relw": 1.0, "relh": 1.0, "anchor": "nw"})
                return
            for k in kids:
                if k is not self and k._layout_sent and k._layout_sent.get("relw") == 1.0:
                    k._layout_sent = None
                    k._auto_layout()
        x, y = (self._pos[0], self._pos[1]) if self._pos else (-1, -1)
        o = {"m": "place", "x": max(0, x), "y": max(0, y), "anchor": "nw"}
        self._layout(o)

    def _layout(self, opts):
        self._layout_sent = opts
        _g.bridge().send({"op": "layout", "id": self._id, "opts": opts})

    # ---- 情報

    def GetId(self):
        return self._wid

    def SetId(self, i):
        self._wid = i

    def GetParent(self):
        return self._parent

    GetGrandParent = lambda self: self._parent._parent if self._parent else None

    def GetChildren(self):
        return [c for c in self._children if not c._destroyed]

    def GetTopLevelParent(self):
        w = self
        while w is not None and not isinstance(w, TopLevelWindow):
            w = w._parent
        return w

    def GetName(self):
        return self._name

    def SetName(self, n):
        self._name = n

    def FindWindowById(self, id):
        for c in self.GetChildren():
            if c._wid == id:
                return c
            r = c.FindWindowById(id)
            if r:
                return r
        return None

    FindWindow = FindWindowById

    def FindWindowByName(self, name):
        for c in self.GetChildren():
            if c._name == name:
                return c
            r = c.FindWindowByName(name)
            if r:
                return r
        return None

    def GetHandle(self):
        return self._id

    def IsBeingDeleted(self):
        return self._destroyed

    def __bool__(self):
        return not self._destroyed

    # ---- 大きさ

    def GetSize(self):
        s = self._size if self._size != (0, 0) else self._req
        return Size(max(0, s[0]), max(0, s[1]))

    def GetClientSize(self):
        return self.GetSize()

    GetVirtualSize = GetBestSize = GetEffectiveMinSize = GetClientSize

    def GetRect(self):
        s = self.GetSize()
        return Rect(0, 0, s[0], s[1])

    GetClientRect = GetScreenRect = GetRect

    def SetSize(self, *args):
        if len(args) == 1:
            w, h = args[0][0], args[0][1]
        elif len(args) >= 4:
            w, h = args[2], args[3]
        else:
            w, h = args[0], args[1]
        self._req = (int(w), int(h))
        self._refresh_props()

    SetClientSize = SetSize

    def SetInitialSize(self, size=DefaultSize):
        self.SetSize(size)

    def SetMinSize(self, size):
        self._min = (int(size[0]), int(size[1]))
        self._refresh_props()

    def GetMinSize(self):
        return Size(*self._min)

    def SetMaxSize(self, size):
        pass

    SetMinClientSize = SetMinSize
    SetMaxClientSize = SetMaxSize

    def SetSizeHints(self, *a, **kw):
        if a and isinstance(a[0], (tuple, list, Size)):
            self.SetMinSize(a[0])
        elif len(a) >= 2:
            self.SetMinSize((a[0], a[1]))

    def GetPosition(self):
        return Point(self._pos if self._pos else (0, 0))

    GetScreenPosition = GetPosition

    def SetPosition(self, pt):
        self._pos = Point(pt)
        if self._containing_sizer is None:
            self._auto_layout()

    Move = SetPosition

    def ClientToScreen(self, pt):
        return Point(pt)

    ScreenToClient = ClientToScreen

    def GetCharWidth(self):
        return 8

    def GetCharHeight(self):
        return 16

    def GetTextExtent(self, s):
        return Size(int(len(str(s)) * 7.5), 17)

    GetFullTextExtent = GetTextExtent

    def FromDIP(self, v):
        return v

    ToDIP = FromDIP

    def GetContentScaleFactor(self):
        return 1.0

    GetDPIScaleFactor = GetContentScaleFactor

    def ConvertDialogToPixels(self, v):
        return v

    # ---- 見た目

    def SetBackgroundColour(self, c):
        self._bg = c
        self._refresh_props()
        return True

    SetOwnBackgroundColour = SetBackgroundColour

    def GetBackgroundColour(self):
        return Colour(self._bg) if self._bg is not None else Colour(255, 255, 255)

    def SetForegroundColour(self, c):
        self._fg = c
        self._refresh_props()
        return True

    SetOwnForegroundColour = SetForegroundColour

    def GetForegroundColour(self):
        return Colour(self._fg) if self._fg is not None else Colour(0, 0, 0)

    def SetFont(self, f):
        self._font = f
        self._refresh_props()
        return True

    SetOwnFont = SetFont

    def GetFont(self):
        return self._font or Font(10)

    def SetToolTip(self, tip):
        self._tooltip = tip if isinstance(tip, str) else getattr(tip, "_tip", "")
        self._send(tooltip=self._tooltip)

    SetToolTipString = SetToolTip

    def SetCursor(self, c):
        pass

    def SetWindowStyle(self, s):
        self._style = s

    SetWindowStyleFlag = SetWindowStyle

    def GetWindowStyle(self):
        return self._style

    GetWindowStyleFlag = GetWindowStyle

    def HasFlag(self, f):
        return bool(self._style & f)

    def SetDoubleBuffered(self, on):
        pass

    def SetBackgroundStyle(self, s):
        pass

    def SetTransparent(self, a):
        pass

    def SetHelpText(self, t):
        pass

    def SetLabel(self, label):
        self._label = str(label)
        self._refresh_props()

    def GetLabel(self):
        return self._label

    GetLabelText = GetLabel

    def SetThemeEnabled(self, e):
        pass

    # ---- 状態

    def Show(self, show=True):
        self._shown = bool(show)
        self._send(visible=self._shown)
        if isinstance(self, TopLevelWindow) and show:
            _g.bridge().send({"op": "raise", "id": self._id})
            if self._paint_bound():
                CallAfter(self._paint)
        return True

    def Hide(self):
        return self.Show(False)

    def IsShown(self):
        return self._shown and not self._destroyed

    IsShownOnScreen = IsShown

    def Enable(self, enable=True):
        self._enabled = bool(enable)
        self._refresh_props()
        return True

    def Disable(self):
        return self.Enable(False)

    def IsEnabled(self):
        return self._enabled

    IsThisEnabled = IsEnabled

    def SetFocus(self):
        top = self.GetTopLevelParent()
        if top is not None:
            top._focus = self
        _g.bridge().send({"op": "focus", "id": self._id})

    SetFocusFromKbd = SetFocus

    def HasFocus(self):
        top = self.GetTopLevelParent()
        return top is not None and getattr(top, "_focus", None) is self

    @staticmethod
    def FindFocus():
        return None

    def AcceptsFocus(self):
        return True

    def Raise(self):
        _g.bridge().send({"op": "raise", "id": self._id})

    def Lower(self):
        pass

    def Refresh(self, eraseBackground=True, rect=None):
        if self._paint_bound():
            CallAfter(self._paint)

    def RefreshRect(self, rect, eraseBackground=True):
        self.Refresh()

    def Update(self):
        _g.bridge().flush()

    def Freeze(self):
        pass

    def Thaw(self):
        pass

    def IsFrozen(self):
        return False

    def CaptureMouse(self):
        pass

    def ReleaseMouse(self):
        pass

    def HasCapture(self):
        return False

    def SetScrollbars(self, *a, **kw):
        pass

    SetScrollRate = EnableScrolling = ShowScrollbars = SetupScrolling = SetVirtualSize = SetScrollbars

    def Scroll(self, *a):
        pass

    def GetViewStart(self):
        return Point(0, 0)

    def CalcUnscrolledPosition(self, *a):
        return Point(a[0], a[1]) if len(a) == 2 else Point(a[0])

    CalcScrolledPosition = CalcUnscrolledPosition

    def PopupMenu(self, menu, pos=DefaultPosition):
        key = _g.bridge().request({"op": "popup", "items": menu._json(), "id": self._id,
                                   "x": pos[0], "y": pos[1]})
        if key is not None:
            item = _menu_items.get(int(key))
            if item is not None:
                item._clicked(self)
        return True

    def Center(self, direction=BOTH):
        pass

    Centre = CenterOnParent = CentreOnParent = CenterOnScreen = CentreOnScreen = Center

    def Fit(self):
        pass

    FitInside = Fit

    def Layout(self):
        return True

    def SendSizeEvent(self, flags=0):
        pass

    def SetAutoLayout(self, b):
        pass

    def InvalidateBestSize(self):
        pass

    def SetAcceleratorTable(self, t):
        pass

    def SetDropTarget(self, t):
        pass

    def DragAcceptFiles(self, b):
        pass

    def Validate(self):
        return True

    def TransferDataToWindow(self):
        return True

    def TransferDataFromWindow(self):
        return True

    def SetValidator(self, v):
        pass

    def GetValidator(self):
        return None

    def SetExtraStyle(self, s):
        pass

    def GetClassName(self):
        return type(self).__name__

    # ---- Sizer

    def SetSizer(self, sizer, deleteOld=True):
        self._sizer = sizer
        if sizer is not None:
            sizer._attach(self, self)

    def SetSizerAndFit(self, sizer, deleteOld=True):
        self.SetSizer(sizer)

    def GetSizer(self):
        return self._sizer

    def GetContainingSizer(self):
        return self._containing_sizer

    def SetContainingSizer(self, s):
        self._containing_sizer = s

    # ---- 片付け

    def Destroy(self):
        if self._destroyed:
            return True
        _emit(self, Event("destroy", self))
        for c in list(self._children):
            c._destroy_quiet()
        self._destroyed = True
        b = _g.bridge()
        b.handlers.pop(self._id, None)
        b.send({"op": "destroy", "id": self._id})
        if self._parent is not None and self in self._parent._children:
            self._parent._children.remove(self)
        if self in _tops:
            _tops.remove(self)
            if not any(not w._destroyed for w in _tops):
                b.quit_all()
        return True

    def DestroyChildren(self):
        for c in list(self._children):
            c.Destroy()

    def _destroy_quiet(self):
        for c in list(self._children):
            c._destroy_quiet()
        self._destroyed = True
        _g.bridge().handlers.pop(self._id, None)
        if self in _tops:
            _tops.remove(self)

    def Close(self, force=False):
        ev = Event("close", self, self._wid)
        if not self._handle(ev) or force:
            if not ev._vetoed or force:
                self.Destroy()
        return True

    # ---- Android からの出来事

    _MOUSE = ("left_down", "left_up", "left_dclick", "right_down", "right_up", "motion", "wheel")

    def _paint_bound(self):
        return any(h[0] == "paint" for h in getattr(self, "_handlers", ()))

    def _on_bound(self, types):
        if any(t in self._MOUSE for t in types) and not self._mouse:
            self._mouse = True
            self._send(mouse=True)
        if "paint" in types or "size" in types:
            self._send(conf=True)
            if "paint" in types:
                CallAfter(self._paint)

    def _paint(self):
        if self._destroyed or not self._paint_bound():
            return
        self._draw = []
        self._handle(Event("paint", self, self._wid))
        draw, self._draw = self._draw, None
        self._send(draw=draw or [])

    def _on_android(self, ev):
        kind = ev.get("ev")
        if kind == "configure":
            old = self._size
            self._size = (int(ev.get("w", 0)), int(ev.get("h", 0)))
            if old != self._size:
                self._handle(Event("size", self, self._wid, w=self._size[0], h=self._size[1]))
                if self._paint_bound():
                    self._paint()
        elif kind == "mouse":
            t = ev.get("type")
            x, y = ev.get("x", 0), ev.get("y", 0)
            mods = ev.get("mods") or ()
            if t == "press":
                self._mouse_down = True
                self._handle(Event("left_down", self, self._wid, x=x, y=y, down=True, mods=mods))
            elif t == "double":
                self._mouse_down = True
                if not self._handle(Event("left_dclick", self, self._wid, x=x, y=y, down=True, mods=mods)):
                    self._handle(Event("left_down", self, self._wid, x=x, y=y, down=True, mods=mods))
            elif t == "release":
                self._mouse_down = False
                self._handle(Event("left_up", self, self._wid, x=x, y=y, down=False, mods=mods))
            elif t == "motion":
                self._handle(Event("motion", self, self._wid, x=x, y=y,
                                   down=getattr(self, "_mouse_down", False), mods=mods))
            elif t == "long":
                self._handle(Event("right_down", self, self._wid, x=x, y=y, mods=mods))
                _emit(self, Event("context_menu", self, self._wid, x=x, y=y))
            elif t == "wheel":
                self._handle(Event("wheel", self, self._wid, x=x, y=y, delta=ev.get("delta", 0) * 120))
        else:
            self._on_event(kind, ev)

    def _on_event(self, kind, ev):
        pass


class Control(Window):
    _type = "label"
    _container = False

    def Command(self, ev):
        _emit(self, ev)


class Panel(Window):
    _type = "frame"

    def _extra_init(self, **kw):
        pass

    def InitDialog(self):
        pass

    def SetDefaultItem(self, w):
        pass


class ScrolledWindow(Panel):
    def _props(self):
        p = Panel._props(self)
        p["scroll"] = "v"
        return p


ScrolledPanel = ScrolledCanvas = VScrolledWindow = ScrolledWindow


class Choicebook(Panel):
    pass


class TopLevelWindow(Window):
    _type = "window"

    def _extra_init(self, title="", **kw):
        self._title = str(title)
        self._menubar = None
        self._status = None
        self._focus = None
        self._modal = None
        self._shown = False
        _tops.append(self)

    def _create(self):
        _g.bridge().send({"op": "new", "id": self._id, "type": "window", "props": self._props()})

    def _props(self):
        p = Window._props(self)
        p.pop("mouse", None)
        p["title"] = self._title
        if self._menubar is not None:
            p["menu"] = self._menubar._json()
        if self._status is not None:
            p["status"] = self._status._text()
        if isinstance(self, Dialog) or self._parent is not None:
            p["toplevel"] = True
        p["visible"] = self._shown
        p["conf"] = True
        return p

    def SetTitle(self, t):
        self._title = str(t)
        self._send(title=self._title)

    def GetTitle(self):
        return self._title

    SetLabel = SetTitle
    GetLabel = GetTitle

    def SetIcon(self, icon):
        pass

    SetIcons = SetIcon

    def Maximize(self, maximize=True):
        pass

    def Iconize(self, iconize=True):
        pass

    def IsMaximized(self):
        return True

    def IsIconized(self):
        return False

    def ShowFullScreen(self, show, style=0):
        return True

    def IsFullScreen(self):
        return False

    def RequestUserAttention(self, flags=0):
        pass

    def SetMenuBar(self, menubar):
        self._menubar = menubar
        if menubar is not None:
            menubar._frame = self
        self._refresh_props()

    def GetMenuBar(self):
        return self._menubar

    def CreateStatusBar(self, number=1, style=0, id=ID_ANY, name=""):
        self._status = StatusBar(self)
        self._status.SetFieldsCount(number)
        self._refresh_props()
        return self._status

    def SetStatusBar(self, sb):
        self._status = sb
        sb._frame = self
        self._refresh_props()

    def GetStatusBar(self):
        return self._status

    def SetStatusText(self, text, number=0):
        if self._status is None:
            self.CreateStatusBar()
        self._status.SetStatusText(text, number)

    def SetStatusWidths(self, widths):
        pass

    def CreateToolBar(self, style=0, id=ID_ANY, name=""):
        self._toolbar = ToolBar(self)
        return self._toolbar

    def GetToolBar(self):
        return getattr(self, "_toolbar", None)

    def SetToolBar(self, tb):
        self._toolbar = tb

    def _on_event(self, kind, ev):
        if kind == "close":
            ev2 = Event("close", self, self._wid)
            handled = self._handle(ev2)
            if not handled and not ev2._vetoed:
                self.Destroy()
        elif kind == "menu":
            key = ev.get("key")
            try:
                item = _menu_items.get(int(key))
            except (TypeError, ValueError):
                item = None
            if item is not None:
                item._clicked(self)
            else:
                tb = getattr(self, "_toolbar", None)
                if tb is not None:
                    tb._clicked(key)
        elif kind == "key":
            self._on_key(ev)

    def _on_key(self, ev):
        target = self._focus if self._focus is not None and not self._focus._destroyed else self
        keysym = ev.get("keysym") or ""
        ch = ev.get("char") or ""
        code = _WXK.get(keysym)
        if code is None:
            code = ord(ch.upper()) if len(ch) == 1 else 0
        mods = ev.get("mods") or ()
        t = "key_down" if ev.get("type", "press") == "press" else "key_up"
        e = Event(t, target, getattr(target, "_wid", ID_ANY), keycode=code, char=ch, mods=mods)
        if t == "key_down":
            hook = Event("char_hook", target, target._wid, keycode=code, char=ch, mods=mods)
            if self._handle(hook):
                return
            if target._handle(e) and not e._skipped:
                return
            if ch or code in (WXK_RETURN, WXK_BACK, WXK_TAB, WXK_ESCAPE):
                target._handle(Event("char", target, target._wid, keycode=code, char=ch, mods=mods))
        else:
            target._handle(e)


_WXK = {"Return": WXK_RETURN, "BackSpace": WXK_BACK, "Tab": WXK_TAB, "Escape": WXK_ESCAPE,
        "space": WXK_SPACE, "Delete": WXK_DELETE, "Left": WXK_LEFT, "Up": WXK_UP, "Right": WXK_RIGHT,
        "Down": WXK_DOWN, "Home": WXK_HOME, "End": WXK_END, "Prior": WXK_PAGEUP, "Next": WXK_PAGEDOWN,
        "Shift_L": WXK_SHIFT, "Control_L": WXK_CONTROL, "Alt_L": WXK_ALT}
for _i in range(12):
    _WXK["F%d" % (_i + 1)] = WXK_F1 + _i


class Frame(TopLevelWindow):
    def __init__(self, parent=None, id=ID_ANY, title="", pos=DefaultPosition, size=DefaultSize,
                 style=DEFAULT_FRAME_STYLE, name="frame"):
        Window.__init__(self, parent, id, pos, size, style, name, title=title)


class MiniFrame(Frame):
    pass


class Dialog(TopLevelWindow):
    def __init__(self, parent=None, id=ID_ANY, title="", pos=DefaultPosition, size=DefaultSize,
                 style=DEFAULT_DIALOG_STYLE, name="dialog"):
        Window.__init__(self, parent, id, pos, size, style, name, title=title)
        self._retcode = 0
        self._modal_running = False
        self._affirmative = ID_OK
        self._escape = ID_CANCEL

    def ShowModal(self):
        self.Show(True)
        self._modal_running = True
        _g.bridge().mainloop(stop=lambda: not self._modal_running or self._destroyed)
        self.Show(False)
        return self._retcode

    def EndModal(self, retCode):
        self._retcode = retCode
        self._modal_running = False

    def IsModal(self):
        return self._modal_running

    def GetReturnCode(self):
        return self._retcode

    def SetReturnCode(self, c):
        self._retcode = c

    def SetAffirmativeId(self, i):
        self._affirmative = i

    def SetEscapeId(self, i):
        self._escape = i

    def CreateButtonSizer(self, flags):
        s = BoxSizer(HORIZONTAL)
        s.AddStretchSpacer()
        for f, i in ((OK, ID_OK), (YES, ID_YES), (NO, ID_NO), (CANCEL, ID_CANCEL)):
            if flags & f:
                s.Add(Button(self, i), 0, ALL, 4)
        return s

    CreateStdDialogButtonSizer = CreateSeparatedButtonSizer = CreateButtonSizer

    def CreateTextSizer(self, message):
        s = BoxSizer(VERTICAL)
        s.Add(StaticText(self, label=message))
        return s

    def _handle(self, ev):
        if Window._handle(self, ev):
            return True
        # 標準の ID のボタンは、押すとダイアログを閉じる
        if ev._type == "button" and ev._id in (ID_OK, ID_CANCEL, ID_YES, ID_NO, ID_APPLY, ID_CLOSE,
                                                self._affirmative, self._escape):
            if self._modal_running:
                self.EndModal(ev._id)
            else:
                self.Show(False)
            return True
        return False

    def _on_event(self, kind, ev):
        if kind == "close" and self._modal_running:
            self.EndModal(ID_CANCEL)
            return
        TopLevelWindow._on_event(self, kind, ev)


class StatusBar(Window):
    def __init__(self, parent, id=ID_ANY, style=0, name="statusBar"):
        self._frame = parent
        self._fields = [""]
        self._eh_init()
        self._destroyed = False
        self._id = None
        self._children = []
        self._parent = parent

    def SetFieldsCount(self, number=1, widths=None):
        self._fields = (self._fields + [""] * number)[:max(1, number)]

    def GetFieldsCount(self):
        return len(self._fields)

    def SetStatusText(self, text, i=0):
        while len(self._fields) <= i:
            self._fields.append("")
        self._fields[i] = str(text)
        if self._frame is not None and not self._frame._destroyed:
            self._frame._send(status=self._text())

    def GetStatusText(self, i=0):
        return self._fields[i] if i < len(self._fields) else ""

    def _text(self):
        return "   |   ".join(f for f in self._fields if f)

    def SetStatusWidths(self, w):
        pass

    def SetStatusStyles(self, s):
        pass

    def PushStatusText(self, text, i=0):
        self.SetStatusText(text, i)

    PopStatusText = lambda self, i=0: None

    def Destroy(self):
        return True

    def _destroy_quiet(self):
        pass


class ToolBar:
    def __init__(self, frame):
        self._frame = frame
        self._tools = []

    def AddTool(self, toolId, label="", bitmap=None, shortHelp="", kind=ITEM_NORMAL, *a, **kw):
        if not isinstance(label, str):
            label, shortHelp = shortHelp or "ツール", label
        self._tools.append((toolId, label or shortHelp or "ツール"))
        return toolId

    AddLabelTool = AddSimpleTool = AddTool

    def AddSeparator(self):
        pass

    def AddStretchableSpace(self):
        pass

    def AddControl(self, control, label=""):
        pass

    def Realize(self):
        # ツールバーの項目は窓の見出しのメニューの先頭に並べる
        mb = self._frame._menubar or MenuBar()
        mb._tools = self._tools
        self._frame._menubar = mb
        mb._frame = self._frame
        self._frame._refresh_props()

    def EnableTool(self, toolId, enable):
        pass

    def ToggleTool(self, toolId, toggle):
        pass

    def SetToolBitmapSize(self, size):
        pass

    def _clicked(self, key):
        try:
            tid = int(str(key).split(":")[-1])
        except ValueError:
            return
        self._frame._handle(Event("menu", self._frame, tid))


# ===================================================================== メニュー

_menu_items = {}


class MenuItem:
    def __init__(self, parentMenu=None, id=ID_ANY, text="", helpString="", kind=ITEM_NORMAL, subMenu=None):
        self._menu = parentMenu
        self._wid = id if id not in (ID_ANY, -1) else NewIdRef()
        self._text = text or _STOCK_LABELS.get(id, "")
        self._help = helpString
        self._kind = kind
        self._sub = subMenu
        self._checked = False
        self._enabled = True
        self._key = next(_menu_keys)
        _menu_items[self._key] = self

    def GetId(self):
        return self._wid

    def GetItemLabel(self):
        return self._text

    GetText = GetItemLabel

    def GetItemLabelText(self):
        return self._text.split("\t")[0].replace("&", "")

    GetLabel = GetItemLabelText

    def SetItemLabel(self, t):
        self._text = t
        self._changed()

    SetText = SetItemLabel

    def Check(self, check=True):
        if self._kind == ITEM_RADIO and check and self._menu is not None:
            for it in self._menu._radio_group(self):
                it._checked = False
        self._checked = bool(check)
        self._changed()

    def IsChecked(self):
        return self._checked

    def Enable(self, enable=True):
        self._enabled = bool(enable)
        self._changed()

    def IsEnabled(self):
        return self._enabled

    def IsCheckable(self):
        return self._kind in (ITEM_CHECK, ITEM_RADIO)

    def GetKind(self):
        return self._kind

    def GetSubMenu(self):
        return self._sub

    def SetSubMenu(self, m):
        self._sub = m

    def GetMenu(self):
        return self._menu

    def SetBitmap(self, bmp):
        pass

    def GetHelp(self):
        return self._help

    def IsSeparator(self):
        return self._kind == ITEM_SEPARATOR

    def IsSubMenu(self):
        return self._sub is not None

    def _changed(self):
        if self._menu is not None:
            self._menu._changed()

    def _json(self):
        if self._kind == ITEM_SEPARATOR:
            return {"type": "separator"}
        label = self._text.split("\t")
        d = {"label": label[0].replace("&", ""), "key": str(self._key), "enabled": self._enabled}
        if len(label) > 1:
            d["accel"] = label[1]
        if self._sub is not None:
            d["type"] = "cascade"
            d["items"] = self._sub._json()
        elif self._kind == ITEM_CHECK:
            d["type"] = "checkbutton"
            d["checked"] = self._checked
        elif self._kind == ITEM_RADIO:
            d["type"] = "radiobutton"
            d["checked"] = self._checked
        else:
            d["type"] = "command"
        return d

    def _clicked(self, frame):
        if not self._enabled:
            return
        if self._kind == ITEM_CHECK:
            self._checked = not self._checked
            self._changed()
        elif self._kind == ITEM_RADIO:
            self.Check(True)
        ev = Event("menu", self, self._wid, int=1 if self._checked else 0)
        handled = self._menu is not None and self._menu._handle(ev)
        if not handled and frame is not None:
            target = frame if isinstance(frame, TopLevelWindow) else frame.GetTopLevelParent() or frame
            if not target._handle(ev) and App._instance is not None:
                App._instance._handle(ev)


_menu_keys = itertools.count(1)


class Menu(EvtHandler):
    def __init__(self, title="", style=0):
        self._eh_init()
        self._items = []
        self._title = title
        self._parents = set()
        self._bar = None

    def _changed(self):
        if self._bar is not None:
            self._bar._changed()
        for p in list(self._parents):
            p._changed()

    def _json(self):
        return [it._json() for it in self._items]

    def _radio_group(self, item):
        i = self._items.index(item)
        a = i
        while a > 0 and self._items[a - 1]._kind == ITEM_RADIO:
            a -= 1
        b = i
        while b + 1 < len(self._items) and self._items[b + 1]._kind == ITEM_RADIO:
            b += 1
        return self._items[a:b + 1]

    def Append(self, id=ID_ANY, item="", helpString="", kind=ITEM_NORMAL, subMenu=None):
        if isinstance(id, MenuItem):
            it = id
            it._menu = self
        else:
            if isinstance(item, Menu):
                subMenu, item = item, helpString
            it = MenuItem(self, id, item, helpString, kind, subMenu)
        if it._sub is not None:
            it._sub._parents.add(self)
        if it._kind == ITEM_RADIO and not any(x._kind == ITEM_RADIO and x._checked for x in self._items[-1:] if True):
            if not self._items or self._items[-1]._kind != ITEM_RADIO:
                it._checked = True
        self._items.append(it)
        self._changed()
        return it

    def AppendItem(self, item):
        return self.Append(item)

    Insert = lambda self, pos, *a, **kw: self.Append(*a, **kw)
    Prepend = Append

    def AppendSeparator(self):
        return self.Append(MenuItem(self, ID_SEPARATOR, "", "", ITEM_SEPARATOR))

    PrependSeparator = InsertSeparator = AppendSeparator

    def AppendCheckItem(self, id, item, help=""):
        return self.Append(id, item, help, ITEM_CHECK)

    def AppendRadioItem(self, id, item, help=""):
        return self.Append(id, item, help, ITEM_RADIO)

    def AppendSubMenu(self, submenu, text, help=""):
        return self.Append(ID_ANY, text, help, ITEM_NORMAL, submenu)

    def AppendMenu(self, id, text, submenu, help=""):
        return self.Append(id, text, help, ITEM_NORMAL, submenu)

    def FindItemById(self, id):
        for it in self._items:
            if it._wid == id:
                return it
            if it._sub is not None:
                r = it._sub.FindItemById(id)
                if r:
                    return r
        return None

    FindItem = FindItemById

    def Check(self, id, check):
        it = self.FindItemById(id)
        if it:
            it.Check(check)

    def IsChecked(self, id):
        it = self.FindItemById(id)
        return bool(it and it.IsChecked())

    def Enable(self, id, enable):
        it = self.FindItemById(id)
        if it:
            it.Enable(enable)

    def IsEnabled(self, id):
        it = self.FindItemById(id)
        return bool(it and it.IsEnabled())

    def SetLabel(self, id, label):
        it = self.FindItemById(id)
        if it:
            it.SetItemLabel(label)

    def GetLabel(self, id):
        it = self.FindItemById(id)
        return it.GetItemLabel() if it else ""

    def Remove(self, item):
        if not isinstance(item, MenuItem):
            item = self.FindItemById(item)
        if item in self._items:
            self._items.remove(item)
            self._changed()
        return item

    Delete = Destroy = Remove

    def GetMenuItems(self):
        return list(self._items)

    def GetMenuItemCount(self):
        return len(self._items)

    def GetTitle(self):
        return self._title

    def SetTitle(self, t):
        self._title = t


class MenuBar(EvtHandler):
    def __init__(self, style=0):
        self._eh_init()
        self._menus = []
        self._frame = None
        self._tools = []

    def _changed(self):
        if self._frame is not None and not self._frame._destroyed:
            self._frame._send(menu=self._json())

    def _json(self):
        out = [{"type": "command", "label": label, "key": "tool:%d" % tid, "enabled": True}
               for tid, label in self._tools]
        for m, title in self._menus:
            out.append({"type": "cascade", "label": title.replace("&", ""), "items": m._json(),
                        "key": "", "enabled": True})
        return out

    def Append(self, menu, title):
        menu._bar = self
        self._menus.append((menu, title))
        self._changed()
        return True

    def Insert(self, pos, menu, title):
        menu._bar = self
        self._menus.insert(pos, (menu, title))
        self._changed()
        return True

    def GetMenu(self, i):
        return self._menus[i][0]

    def GetMenuCount(self):
        return len(self._menus)

    def FindMenu(self, title):
        for i, (m, t) in enumerate(self._menus):
            if t.replace("&", "") == title.replace("&", ""):
                return i
        return NOT_FOUND

    def FindItemById(self, id):
        for m, t in self._menus:
            r = m.FindItemById(id)
            if r:
                return r
        return None

    FindItem = FindItemById

    def Check(self, id, check):
        it = self.FindItemById(id)
        if it:
            it.Check(check)

    def IsChecked(self, id):
        it = self.FindItemById(id)
        return bool(it and it.IsChecked())

    def Enable(self, id, enable):
        it = self.FindItemById(id)
        if it:
            it.Enable(enable)

    def EnableTop(self, pos, enable):
        pass

    def SetMenuLabel(self, pos, label):
        m, t = self._menus[pos]
        self._menus[pos] = (m, label)
        self._changed()


# ===================================================================== 部品

class StaticText(Control):
    _type = "label"

    def __init__(self, parent, id=ID_ANY, label="", pos=DefaultPosition, size=DefaultSize, style=0,
                 name="staticText"):
        Window.__init__(self, parent, id, pos, size, style, name, label=label)

    def _props(self):
        p = Window._props(self)
        p["text"] = self._label
        if self._style & ALIGN_CENTER_HORIZONTAL:
            p["justify"] = "center"
            p["anchor"] = "center"
        elif self._style & ALIGN_RIGHT:
            p["justify"] = "right"
            p["anchor"] = "e"
        else:
            p["anchor"] = "w"
        if self._tooltip:
            p["tooltip"] = self._tooltip
        return p

    def Wrap(self, width):
        self._send(wrap=width)

    def SetLabelText(self, t):
        self.SetLabel(t)


class GenStaticText(StaticText):
    pass


class HyperlinkCtrl(StaticText):
    def __init__(self, parent, id=ID_ANY, label="", url="", pos=DefaultPosition, size=DefaultSize,
                 style=0, name="hyperlink"):
        self._url = url
        StaticText.__init__(self, parent, id, label, pos, size, style, name)
        self._send(fg="#0b57d0", link=url, mouse=True)

    def GetURL(self):
        return self._url

    def SetURL(self, u):
        self._url = u

    def _on_android(self, ev):
        if ev.get("ev") == "mouse" and ev.get("type") == "release":
            if not _emit(self, Event("hyperlink", self, self._wid, string=self._url)):
                LaunchDefaultBrowser(self._url)
            return
        Window._on_android(self, ev)


class StaticLine(Control):
    _type = "separator"

    def __init__(self, parent, id=ID_ANY, pos=DefaultPosition, size=DefaultSize, style=LI_HORIZONTAL,
                 name="staticLine"):
        Window.__init__(self, parent, id, pos, size, style, name)

    def _props(self):
        p = Window._props(self)
        p["orient"] = "vertical" if self._style & LI_VERTICAL else "horizontal"
        return p


class StaticBitmap(Control):
    _type = "label"

    def __init__(self, parent, id=ID_ANY, bitmap=NullBitmap, pos=DefaultPosition, size=DefaultSize,
                 style=0, name="staticBitmap"):
        self._bitmap = bitmap
        Window.__init__(self, parent, id, pos, size, style, name)

    def _props(self):
        p = Window._props(self)
        ref = self._bitmap._ref() if isinstance(self._bitmap, Bitmap) else None
        if ref:
            p["image"] = ref
            p["compound"] = "none"
        return p

    def SetBitmap(self, b):
        self._bitmap = b
        self._refresh_props()

    def GetBitmap(self):
        return self._bitmap


class StaticBox(Window):
    _type = "labelframe"

    def __init__(self, parent, id=ID_ANY, label="", pos=DefaultPosition, size=DefaultSize, style=0,
                 name="staticBox"):
        Window.__init__(self, parent, id, pos, size, style, name, label=label)

    def _props(self):
        p = Window._props(self)
        p["text"] = self._label
        return p


class Button(Control):
    _type = "button"
    _evt = "button"

    def __init__(self, parent, id=ID_ANY, label="", pos=DefaultPosition, size=DefaultSize, style=0,
                 validator=None, name="button"):
        if not label and id in _STOCK_LABELS:
            label = _STOCK_LABELS[id]
        Window.__init__(self, parent, id, pos, size, style, name, label=label)

    def _props(self):
        p = Window._props(self)
        p["text"] = self._label.replace("&", "")
        p["style"] = "filled" if self._wid in (ID_OK, ID_YES) or getattr(self, "_default", False) else "tonal"
        if self._tooltip:
            p["tooltip"] = self._tooltip
        return p

    def SetDefault(self):
        self._default = True
        self._refresh_props()

    def SetBitmap(self, b, dir=LEFT):
        pass

    SetBitmapLabel = SetBitmapPressed = SetBitmapDisabled = SetBitmapFocus = SetBitmapCurrent = SetBitmap

    @staticmethod
    def GetDefaultSize(win=None):
        return Size(88, 36)

    def _on_event(self, kind, ev):
        if kind == "command":
            _emit(self, Event(self._evt, self, self._wid))


class BitmapButton(Button):
    def __init__(self, parent, id=ID_ANY, bitmap=NullBitmap, pos=DefaultPosition, size=DefaultSize,
                 style=0, validator=None, name="button"):
        self._bitmap = bitmap
        Button.__init__(self, parent, id, "", pos, size, style, validator, name)

    def _props(self):
        p = Button._props(self)
        ref = self._bitmap._ref() if isinstance(self._bitmap, Bitmap) else None
        if ref:
            p["image"] = ref
        elif not p["text"]:
            p["text"] = "●"
        return p


class ToggleButton(Button):
    _type = "button"
    _evt = "toggle"

    def __init__(self, parent, id=ID_ANY, label="", pos=DefaultPosition, size=DefaultSize, style=0,
                 validator=None, name="toggle"):
        self._value = False
        Button.__init__(self, parent, id, label, pos, size, style, validator, name)

    def _props(self):
        p = Button._props(self)
        p["style"] = "filled" if self._value else "outlined"
        return p

    def GetValue(self):
        return self._value

    def SetValue(self, v):
        self._value = bool(v)
        self._refresh_props()

    def _on_event(self, kind, ev):
        if kind == "command":
            self._value = not self._value
            self._refresh_props()
            _emit(self, Event("toggle", self, self._wid, int=1 if self._value else 0))


class TextCtrl(Control):
    _type = "entry"

    def __init__(self, parent, id=ID_ANY, value="", pos=DefaultPosition, size=DefaultSize, style=0,
                 validator=None, name="text"):
        self._value = str(value)
        self._hint = ""
        self._type = "text" if style & TE_MULTILINE else "entry"
        Window.__init__(self, parent, id, pos, size, style, name)

    def _props(self):
        p = Window._props(self)
        p["value"] = self._value
        if self._style & TE_PASSWORD:
            p["password"] = True
        if self._style & TE_READONLY:
            p["state"] = "readonly"
        if self._style & TE_CENTER:
            p["justify"] = "center"
        elif self._style & TE_RIGHT:
            p["justify"] = "right"
        if self._hint:
            p["hint"] = self._hint
        if self._type == "text":
            p.setdefault("hlines", 5)
            if self._style & HSCROLL:
                p["nowrap"] = True
        else:
            p.setdefault("wchars", 16)
        return p

    def GetValue(self):
        return self._value

    def SetValue(self, v):
        self._value = str(v)
        self._send(value=self._value)
        _emit(self, Event("text", self, self._wid, string=self._value))

    def ChangeValue(self, v):
        self._value = str(v)
        self._send(value=self._value)

    def AppendText(self, t):
        self.SetValue(self._value + str(t))

    WriteText = AppendText

    def Clear(self):
        self.SetValue("")

    def GetLineText(self, n):
        lines = self._value.split("\n")
        return lines[n] if 0 <= n < len(lines) else ""

    def GetNumberOfLines(self):
        return self._value.count("\n") + 1

    def GetLastPosition(self):
        return len(self._value)

    def GetInsertionPoint(self):
        return len(self._value)

    def SetInsertionPoint(self, p):
        pass

    SetInsertionPointEnd = SetSelection = SelectAll = SelectNone = ShowPosition = lambda self, *a: None

    def GetRange(self, a, b):
        return self._value[a:b]

    def GetStringSelection(self):
        return ""

    def GetSelection(self):
        return (0, 0)

    def Remove(self, a, b):
        self.SetValue(self._value[:a] + self._value[b:])

    def Replace(self, a, b, t):
        self.SetValue(self._value[:a] + str(t) + self._value[b:])

    def IsEmpty(self):
        return not self._value

    def IsModified(self):
        return False

    def SetModified(self, m):
        pass

    def SetEditable(self, e):
        self._style = (self._style & ~TE_READONLY) | (0 if e else TE_READONLY)
        self._refresh_props()

    def IsEditable(self):
        return not self._style & TE_READONLY

    def SetMaxLength(self, n):
        self._send(maxlen=n)

    def SetHint(self, h):
        self._hint = str(h)
        self._refresh_props()
        return True

    def GetHint(self):
        return self._hint

    def SetDefaultStyle(self, style):
        return True

    def SetStyle(self, start, end, style):
        return True

    def CanCopy(self):
        return True

    CanPaste = CanCut = CanUndo = CanRedo = CanCopy
    Copy = Cut = Paste = Undo = Redo = lambda self: None

    def LoadFile(self, path, fileType=0):
        with open(path, encoding="utf-8") as f:
            self.SetValue(f.read())
        return True

    def SaveFile(self, path="", fileType=0):
        with open(path, "w", encoding="utf-8") as f:
            f.write(self._value)
        return True

    def _on_event(self, kind, ev):
        if kind == "value":
            self._value = str(ev.get("value", ""))
            _emit(self, Event("text", self, self._wid, string=self._value))
        elif kind == "submit":
            if self._style & TE_PROCESS_ENTER:
                _emit(self, Event("text_enter", self, self._wid, string=self._value))
            top = self.GetTopLevelParent()
            if top is not None:
                top._focus = self
                top._on_key({"keysym": "Return", "char": "\r", "type": "press"})
        elif kind == "focus":
            top = self.GetTopLevelParent()
            if ev.get("in") and top is not None:
                top._focus = self
            self._handle(Event("set_focus" if ev.get("in") else "kill_focus", self, self._wid))


class SearchCtrl(TextCtrl):
    def ShowSearchButton(self, s):
        pass

    ShowCancelButton = ShowSearchButton

    def SetDescriptiveText(self, t):
        self.SetHint(t)


class CheckBox(Control):
    _type = "check"

    def __init__(self, parent, id=ID_ANY, label="", pos=DefaultPosition, size=DefaultSize, style=0,
                 validator=None, name="check"):
        self._value = False
        Window.__init__(self, parent, id, pos, size, style, name, label=label)

    def _props(self):
        p = Window._props(self)
        p["text"] = self._label
        p["value"] = self._value
        return p

    def GetValue(self):
        return self._value

    IsChecked = GetValue

    def SetValue(self, v):
        self._value = bool(v)
        self._send(value=self._value)

    def Get3StateValue(self):
        return 1 if self._value else 0

    def Set3StateValue(self, v):
        self.SetValue(v)

    def _on_event(self, kind, ev):
        if kind == "value":
            self._value = bool(ev.get("value"))
            _emit(self, Event("checkbox", self, self._wid, int=1 if self._value else 0))


class RadioButton(Control):
    _type = "radio"

    def __init__(self, parent, id=ID_ANY, label="", pos=DefaultPosition, size=DefaultSize, style=0,
                 validator=None, name="radio"):
        self._value = False
        Window.__init__(self, parent, id, pos, size, style, name, label=label)
        group = self._group()
        if len(group) == 1:
            self._value = True
            self._send(value=True)

    def _group(self):
        sibs = [c for c in self._parent._children if isinstance(c, RadioButton) and not c._destroyed]
        i = sibs.index(self)
        a = i
        while a > 0 and not sibs[a]._style & RB_GROUP:
            a -= 1
        b = i + 1
        while b < len(sibs) and not sibs[b]._style & RB_GROUP:
            b += 1
        return sibs[a:b]

    def _props(self):
        p = Window._props(self)
        p["text"] = self._label
        p["value"] = self._value
        return p

    def GetValue(self):
        return self._value

    def SetValue(self, v):
        if v:
            for r in self._group():
                if r is not self and r._value:
                    r._value = False
                    r._send(value=False)
        self._value = bool(v)
        self._send(value=self._value)

    def _on_event(self, kind, ev):
        if kind == "value" and ev.get("value"):
            self.SetValue(True)
            _emit(self, Event("radiobutton", self, self._wid, int=1))


class RadioBox(Window):
    _type = "labelframe"

    def __init__(self, parent, id=ID_ANY, label="", pos=DefaultPosition, size=DefaultSize, choices=[],
                 majorDimension=0, style=RA_SPECIFY_COLS, validator=None, name="radioBox"):
        self._choices = [str(c) for c in choices]
        self._sel = 0
        Window.__init__(self, parent, id, pos, size, style, name, label=label)
        cols = majorDimension or 1
        horizontal = style & RA_SPECIFY_ROWS and majorDimension == 1 or (style & RA_SPECIFY_COLS and majorDimension > 1)
        self._radios = []
        for i, c in enumerate(self._choices):
            r = _RadioItem(self, i, c)
            self._radios.append(r)
            if horizontal:
                r._layout({"m": "grid", "row": i // cols, "col": i % cols, "sticky": "w",
                           "padx": [4, 4], "pady": [2, 2]})
            else:
                r._layout({"m": "grid", "row": i, "col": 0, "sticky": "w", "padx": [4, 4], "pady": [2, 2]})
        self._sync()

    def _props(self):
        p = Window._props(self)
        p["text"] = self._label
        return p

    def _sync(self):
        for i, r in enumerate(self._radios):
            r._send(value=i == self._sel)

    def GetSelection(self):
        return self._sel

    def SetSelection(self, n):
        self._sel = n
        self._sync()

    def GetStringSelection(self):
        return self._choices[self._sel] if self._choices else ""

    def SetStringSelection(self, s):
        if s in self._choices:
            self.SetSelection(self._choices.index(s))

    def GetString(self, n):
        return self._choices[n]

    def GetCount(self):
        return len(self._choices)

    def EnableItem(self, n, enable=True):
        self._radios[n].Enable(enable)

    def ShowItem(self, n, show=True):
        self._radios[n].Show(show)

    def _child_added(self, child):
        pass


class _RadioItem(Control):
    _type = "radio"

    def __init__(self, box, index, label):
        self._box = box
        self._index = index
        Window.__init__(self, box, ID_ANY, DefaultPosition, DefaultSize, 0, "radio", label=label)

    def _props(self):
        p = Window._props(self)
        p["text"] = self._label
        return p

    def _on_event(self, kind, ev):
        if kind == "value" and ev.get("value"):
            self._box._sel = self._index
            self._box._sync()
            _emit(self._box, Event("radiobox", self._box, self._box._wid, int=self._index,
                                   string=self._box._choices[self._index]))


class _ItemContainer(Control):
    """Choice / ComboBox / ListBox の共通部分。"""

    def _items_init(self, choices):
        self._choices = [str(c) for c in (choices or [])]
        self._data = [None] * len(self._choices)
        self._sel = -1

    def Append(self, item, clientData=None):
        if isinstance(item, (list, tuple)):
            for x in item:
                self.Append(x)
            return len(self._choices) - 1
        self._choices.append(str(item))
        self._data.append(clientData)
        self._refresh_props()
        return len(self._choices) - 1

    AppendItems = lambda self, items: self.Append(list(items))

    def Insert(self, item, pos, clientData=None):
        self._choices.insert(pos, str(item))
        self._data.insert(pos, clientData)
        self._refresh_props()
        return pos

    def Set(self, items):
        self._choices = [str(c) for c in items]
        self._data = [None] * len(self._choices)
        self._sel = -1
        self._refresh_props()

    SetItems = Set

    def Clear(self):
        self.Set([])

    def Delete(self, n):
        del self._choices[n]
        del self._data[n]
        if self._sel >= len(self._choices):
            self._sel = len(self._choices) - 1
        self._refresh_props()

    def GetCount(self):
        return len(self._choices)

    def GetString(self, n):
        return self._choices[n] if 0 <= n < len(self._choices) else ""

    def SetString(self, n, s):
        self._choices[n] = str(s)
        self._refresh_props()

    def GetStrings(self):
        return list(self._choices)

    GetItems = GetStrings

    def FindString(self, s, caseSensitive=False):
        for i, c in enumerate(self._choices):
            if c == s or (not caseSensitive and c.lower() == str(s).lower()):
                return i
        return NOT_FOUND

    def GetSelection(self):
        return self._sel

    GetCurrentSelection = GetSelection

    def SetSelection(self, n):
        self._sel = n
        self._refresh_props()

    def GetStringSelection(self):
        return self.GetString(self._sel)

    def SetStringSelection(self, s):
        i = self.FindString(s)
        if i != NOT_FOUND:
            self.SetSelection(i)
            return True
        return False

    def GetClientData(self, n):
        return self._data[n]

    def SetClientData(self, n, d):
        self._data[n] = d

    def IsEmpty(self):
        return not self._choices


class Choice(_ItemContainer):
    _type = "combo"
    _evt = "choice"

    def __init__(self, parent, id=ID_ANY, pos=DefaultPosition, size=DefaultSize, choices=[], style=0,
                 validator=None, name="choice"):
        self._items_init(choices)
        Window.__init__(self, parent, id, pos, size, style, name)

    def _props(self):
        p = Window._props(self)
        p["items"] = self._choices
        p["value"] = self.GetString(self._sel)
        p["readonly"] = True
        return p

    def _on_event(self, kind, ev):
        if kind == "value":
            v = str(ev.get("value", ""))
            if v in self._choices:
                self._sel = self._choices.index(v)
                _emit(self, Event(self._evt, self, self._wid, int=self._sel, string=v))


class ComboBox(_ItemContainer):
    _type = "combo"

    def __init__(self, parent, id=ID_ANY, value="", pos=DefaultPosition, size=DefaultSize, choices=[],
                 style=0, validator=None, name="comboBox"):
        self._items_init(choices)
        self._value = str(value)
        Window.__init__(self, parent, id, pos, size, style, name)

    def _props(self):
        p = Window._props(self)
        p["items"] = self._choices
        p["value"] = self._value
        p["readonly"] = bool(self._style & CB_READONLY)
        return p

    def GetValue(self):
        return self._value

    def SetValue(self, v):
        self._value = str(v)
        self._sel = self._choices.index(self._value) if self._value in self._choices else -1
        self._send(value=self._value)

    def SetSelection(self, n):
        self._sel = n
        self._value = self.GetString(n)
        self._send(value=self._value)

    def _on_event(self, kind, ev):
        if kind == "value":
            self._value = str(ev.get("value", ""))
            self._sel = self._choices.index(self._value) if self._value in self._choices else -1
            if ev.get("selected"):
                _emit(self, Event("combobox", self, self._wid, int=self._sel, string=self._value))
            _emit(self, Event("text", self, self._wid, string=self._value))
        elif kind == "submit":
            _emit(self, Event("text_enter", self, self._wid, string=self._value))


BitmapComboBox = OwnerDrawnComboBox = ComboBox


class ListBox(_ItemContainer):
    _type = "listbox"

    def __init__(self, parent, id=ID_ANY, pos=DefaultPosition, size=DefaultSize, choices=[], style=0,
                 validator=None, name="listBox"):
        self._items_init(choices)
        self._sels = []
        Window.__init__(self, parent, id, pos, size, style, name)

    def _props(self):
        p = Window._props(self)
        p["items"] = self._choices
        p["selection"] = self._sels
        p["select_mode"] = "multiple" if self._style & (LB_MULTIPLE | LB_EXTENDED) else "browse"
        p.setdefault("hlines", 6)
        return p

    def GetSelection(self):
        return self._sels[0] if self._sels else NOT_FOUND

    def GetSelections(self):
        return list(self._sels)

    def SetSelection(self, n, select=True):
        if select:
            self._sels = [n] if not self._style & (LB_MULTIPLE | LB_EXTENDED) else sorted(set(self._sels + [n]))
        elif n in self._sels:
            self._sels.remove(n)
        self._refresh_props()

    Select = SetSelection

    def Deselect(self, n):
        self.SetSelection(n, False)

    def DeselectAll(self):
        self._sels = []
        self._refresh_props()

    def IsSelected(self, n):
        return n in self._sels

    def GetStringSelection(self):
        return self.GetString(self.GetSelection())

    def EnsureVisible(self, n):
        self._send(see=n)

    def SetFirstItem(self, n):
        self.EnsureVisible(n)

    def _on_event(self, kind, ev):
        if kind == "value":
            self._sels = [int(x) for x in (ev.get("value") or [])]
            s = self.GetSelection()
            _emit(self, Event("listbox", self, self._wid, int=s, string=self.GetString(s)))
            if ev.get("double"):
                _emit(self, Event("listbox_dclick", self, self._wid, int=s, string=self.GetString(s)))


class CheckListBox(ListBox):
    def __init__(self, *a, **kw):
        ListBox.__init__(self, *a, **kw)
        self._style |= LB_MULTIPLE
        self._refresh_props()

    def IsChecked(self, n):
        return n in self._sels

    def Check(self, n, check=True):
        self.SetSelection(n, check)

    def GetCheckedItems(self):
        return list(self._sels)

    def GetCheckedStrings(self):
        return [self._choices[i] for i in self._sels]

    def SetCheckedItems(self, items):
        self._sels = list(items)
        self._refresh_props()

    def _on_event(self, kind, ev):
        if kind == "value":
            old = set(self._sels)
            self._sels = [int(x) for x in (ev.get("value") or [])]
            for i in set(self._sels) ^ old:
                _emit(self, Event("checklistbox", self, self._wid, int=i))


class Slider(Control):
    _type = "scale"

    def __init__(self, parent, id=ID_ANY, value=0, minValue=0, maxValue=100, pos=DefaultPosition,
                 size=DefaultSize, style=SL_HORIZONTAL, validator=None, name="slider"):
        self._value, self._min, self._max = value, minValue, maxValue
        Window.__init__(self, parent, id, pos, size, style, name)

    def _props(self):
        p = Window._props(self)
        p.update({"from": self._min, "to": self._max, "step": 1, "value": self._value,
                  "orient": "vertical" if self._style & SL_VERTICAL else "horizontal",
                  "showvalue": bool(self._style & (SL_LABELS | SL_VALUE_LABEL))})
        return p

    def GetValue(self):
        return self._value

    def SetValue(self, v):
        self._value = int(v)
        self._send(value=self._value)

    def SetRange(self, a, b):
        self._min, self._max = a, b
        self._refresh_props()

    def GetMin(self):
        return self._min

    def GetMax(self):
        return self._max

    SetMin = lambda self, v: self.SetRange(v, self._max)
    SetMax = lambda self, v: self.SetRange(self._min, v)
    SetTickFreq = SetPageSize = SetLineSize = lambda self, *a: None

    def _on_event(self, kind, ev):
        if kind == "value":
            self._value = int(round(float(ev.get("value", 0))))
            _emit(self, Event("slider", self, self._wid, int=self._value))


class SpinCtrl(Control):
    _type = "spin"
    _float = False

    def __init__(self, parent, id=ID_ANY, value="", pos=DefaultPosition, size=DefaultSize,
                 style=SP_ARROW_KEYS, min=0, max=100, initial=0, inc=1, name="wxSpinCtrl"):
        self._min, self._max, self._inc = min, max, inc
        try:
            self._value = float(value) if value not in ("", None) else float(initial)
        except ValueError:
            self._value = float(initial)
        Window.__init__(self, parent, id, pos, size, style, name)

    def _fmt(self):
        if self._float:
            return ("%." + str(getattr(self, "_digits", 2)) + "f") % self._value
        return str(int(self._value))

    def _props(self):
        p = Window._props(self)
        p["value"] = self._fmt()
        p.setdefault("wchars", 6)
        return p

    def GetValue(self):
        return self._value if self._float else int(self._value)

    def SetValue(self, v):
        self._value = float(v)
        self._send(value=self._fmt())

    def SetRange(self, a, b):
        self._min, self._max = a, b

    def GetMin(self):
        return self._min

    def GetMax(self):
        return self._max

    def SetIncrement(self, inc):
        self._inc = inc

    def SetDigits(self, d):
        self._digits = d

    def _on_event(self, kind, ev):
        if kind == "command":
            d = 1 if ev.get("dir", "up") == "up" else -1
            v = self._value + self._inc * d
            if self._style & SP_WRAP:
                if v > self._max:
                    v = self._min
                elif v < self._min:
                    v = self._max
            self._value = max(self._min, min(self._max, v))
            self._send(value=self._fmt())
            _emit(self, Event("spinctrl", self, self._wid, int=int(self._value)))
        elif kind == "value":
            try:
                self._value = max(self._min, min(self._max, float(ev.get("value"))))
            except (TypeError, ValueError):
                return
            _emit(self, Event("spinctrl", self, self._wid, int=int(self._value)))


class SpinCtrlDouble(SpinCtrl):
    _float = True

    def __init__(self, parent, id=ID_ANY, value="", pos=DefaultPosition, size=DefaultSize,
                 style=SP_ARROW_KEYS, min=0, max=100, initial=0, inc=1, name="wxSpinCtrlDouble"):
        SpinCtrl.__init__(self, parent, id, value, pos, size, style, min, max, initial, inc, name)


SpinButton = SpinCtrl


class Gauge(Control):
    _type = "progress"

    def __init__(self, parent, id=ID_ANY, range=100, pos=DefaultPosition, size=DefaultSize,
                 style=GA_HORIZONTAL, validator=None, name="gauge"):
        self._range, self._value, self._pulse = range, 0, False
        Window.__init__(self, parent, id, pos, size, style, name)

    def _props(self):
        p = Window._props(self)
        p.update({"value": self._value, "max": self._range,
                  "mode": "indeterminate" if self._pulse else "determinate", "running": self._pulse})
        return p

    def SetValue(self, v):
        self._value, self._pulse = v, False
        self._refresh_props()

    def GetValue(self):
        return self._value

    def SetRange(self, r):
        self._range = r
        self._refresh_props()

    def GetRange(self):
        return self._range

    def Pulse(self):
        if not self._pulse:
            self._pulse = True
            self._refresh_props()


class Notebook(Window):
    _type = "notebook"

    def __init__(self, parent, id=ID_ANY, pos=DefaultPosition, size=DefaultSize, style=0, name="notebook"):
        self._pages = []
        self._sel = 0
        Window.__init__(self, parent, id, pos, size, style, name)

    def _props(self):
        p = Window._props(self)
        p["tabs"] = [t for w, t in self._pages]
        p["selected"] = self._sel
        return p

    def _child_added(self, child):
        pass

    def _sync(self):
        b = _g.bridge()
        for w, t in self._pages:
            w._layout({"m": "tab"})
        b.send({"op": "order", "id": self._id, "ids": [w._id for w, t in self._pages]})
        self._refresh_props()

    def AddPage(self, page, text, select=False, imageId=-1):
        self._pages.append((page, str(text)))
        if select:
            self._sel = len(self._pages) - 1
        self._sync()
        return True

    def InsertPage(self, index, page, text, select=False, imageId=-1):
        self._pages.insert(index, (page, str(text)))
        self._sync()
        return True

    def RemovePage(self, n):
        w, t = self._pages.pop(n)
        w._layout({"m": "none"})
        self._sel = max(0, min(self._sel, len(self._pages) - 1))
        self._sync()
        return True

    def DeletePage(self, n):
        w = self._pages[n][0]
        self.RemovePage(n)
        w.Destroy()
        return True

    def DeleteAllPages(self):
        while self._pages:
            self.DeletePage(0)
        return True

    def GetPage(self, n):
        return self._pages[n][0]

    def GetPageCount(self):
        return len(self._pages)

    def GetCurrentPage(self):
        return self._pages[self._sel][0] if self._pages else None

    def GetSelection(self):
        return self._sel

    def SetSelection(self, n):
        old = self._sel
        self._sel = n
        self._send(selected=n)
        return old

    ChangeSelection = SetSelection

    def GetPageText(self, n):
        return self._pages[n][1]

    def SetPageText(self, n, t):
        self._pages[n] = (self._pages[n][0], str(t))
        self._refresh_props()
        return True

    def SetImageList(self, il):
        pass

    AssignImageList = SetImageList

    def _on_event(self, kind, ev):
        if kind == "tab":
            old = self._sel
            self._sel = int(ev.get("index", 0))
            _emit(self, Event("page_changed", self, self._wid, int=self._sel, old=old))


Listbook = Toolbook = Treebook = Simplebook = Choicebook = Notebook


class ListCtrl(Control):
    _type = "table"

    def __init__(self, parent, id=ID_ANY, pos=DefaultPosition, size=DefaultSize, style=LC_ICON,
                 validator=None, name="listCtrl"):
        self._cols = []
        self._rows = []
        self._data = []
        self._sels = []
        Window.__init__(self, parent, id, pos, size, style, name)

    def _props(self):
        p = Window._props(self)
        report = self._style & LC_REPORT
        p["headings"] = [c[0] for c in self._cols] if report and not self._style & LC_NO_HEADER else []
        ncol = max(1, len(self._cols))
        p["rows"] = [(r + [""] * ncol)[:ncol] if report else r[:1] for r in self._rows]
        p["widths"] = [c[1] for c in self._cols] if report else []
        p["selection"] = self._sels
        p["select_mode"] = "browse" if self._style & LC_SINGLE_SEL else "extended"
        p.setdefault("hlines", 8)
        return p

    def InsertColumn(self, col, heading, format=LIST_FORMAT_LEFT, width=-1):
        if isinstance(heading, ListItem):
            heading = heading._text
        self._cols.insert(col, [str(heading), width if width > 0 else 100])
        for r in self._rows:
            r.insert(col, "")
        self._refresh_props()
        return col

    AppendColumn = lambda self, heading, format=LIST_FORMAT_LEFT, width=-1: \
        self.InsertColumn(len(self._cols), heading, format, width)

    def DeleteColumn(self, col):
        self._cols.pop(col)
        for r in self._rows:
            if col < len(r):
                r.pop(col)
        self._refresh_props()

    def DeleteAllColumns(self):
        self._cols = []
        self._refresh_props()

    def GetColumnCount(self):
        return len(self._cols)

    def SetColumnWidth(self, col, width):
        if 0 <= col < len(self._cols) and width > 0:
            self._cols[col][1] = width
            self._refresh_props()

    def GetColumnWidth(self, col):
        return self._cols[col][1]

    def InsertItem(self, index, label="", imageIndex=-1):
        if isinstance(index, ListItem):
            index, label = index._id, index._text
        index = min(int(index), len(self._rows))
        self._rows.insert(index, [str(label)] + [""] * max(0, len(self._cols) - 1))
        self._data.insert(index, None)
        self._refresh_props()
        return index

    InsertStringItem = InsertItem

    def Append(self, entry):
        i = self.InsertItem(len(self._rows), entry[0] if entry else "")
        for c, v in enumerate(entry[1:], 1):
            self.SetItem(i, c, v)
        return i

    def SetItem(self, index, column=0, label="", imageId=-1):
        if isinstance(index, ListItem):
            index, column, label = index._id, index._col, index._text
        row = self._rows[index]
        while len(row) <= column:
            row.append("")
        row[column] = str(label)
        self._refresh_props()
        return True

    SetStringItem = SetItem

    def SetItemText(self, index, text):
        self.SetItem(index, 0, text)

    def GetItemText(self, item, col=0):
        r = self._rows[item]
        return r[col] if col < len(r) else ""

    def GetItem(self, itemIdx, col=0):
        it = ListItem()
        it._id, it._col, it._text = itemIdx, col, self.GetItemText(itemIdx, col)
        return it

    def GetItemCount(self):
        return len(self._rows)

    def DeleteItem(self, item):
        self._rows.pop(item)
        self._data.pop(item)
        self._sels = [s if s < item else s - 1 for s in self._sels if s != item]
        self._refresh_props()
        return True

    def DeleteAllItems(self):
        self._rows, self._data, self._sels = [], [], []
        self._refresh_props()
        return True

    ClearAll = DeleteAllItems

    def SetItemData(self, item, data):
        self._data[item] = data
        return True

    SetItemPyData = SetItemData

    def GetItemData(self, item):
        return self._data[item]

    GetItemPyData = GetItemData

    def GetFirstSelected(self):
        return self._sels[0] if self._sels else -1

    def GetNextSelected(self, item):
        later = [s for s in self._sels if s > item]
        return later[0] if later else -1

    def GetNextItem(self, item, geometry=LIST_NEXT_ALL, state=0):
        if state & LIST_STATE_SELECTED:
            return self.GetNextSelected(item)
        return item + 1 if item + 1 < len(self._rows) else -1

    def GetSelectedItemCount(self):
        return len(self._sels)

    def IsSelected(self, idx):
        return idx in self._sels

    def Select(self, idx, on=1):
        if on and idx not in self._sels:
            self._sels.append(idx)
        elif not on and idx in self._sels:
            self._sels.remove(idx)
        self._refresh_props()

    def SetItemState(self, item, state, stateMask):
        self.Select(item, bool(state & LIST_STATE_SELECTED))

    def Focus(self, idx):
        pass

    def EnsureVisible(self, idx):
        self._send(see=idx)

    SetItemBackgroundColour = SetItemTextColour = SetItemFont = lambda self, *a: None

    def SetImageList(self, *a):
        pass

    AssignImageList = SetImageList

    def SortItems(self, fn):
        pass

    def _on_event(self, kind, ev):
        if kind == "value":
            old = set(self._sels)
            self._sels = [int(x) for x in (ev.get("value") or [])]
            for i in set(self._sels) - old:
                _emit(self, Event("list_selected", self, self._wid, int=i, string=self.GetItemText(i)))
            for i in old - set(self._sels):
                _emit(self, Event("list_deselected", self, self._wid, int=i))
            if ev.get("double") and self._sels:
                i = self._sels[-1]
                _emit(self, Event("list_activated", self, self._wid, int=i, string=self.GetItemText(i)))


class ListItem:
    def __init__(self):
        self._id, self._col, self._text = 0, 0, ""

    def SetId(self, i):
        self._id = i

    def GetId(self):
        return self._id

    def SetColumn(self, c):
        self._col = c

    def SetText(self, t):
        self._text = str(t)

    def GetText(self):
        return self._text

    SetMask = SetWidth = SetAlign = SetImage = lambda self, *a: None


ListView = ListCtrl


class ToolTip:
    def __init__(self, tip):
        self._tip = tip

    def SetTip(self, t):
        self._tip = t

    @staticmethod
    def Enable(flag):
        pass


class ImageList:
    def __init__(self, *a, **kw):
        self._n = 0

    def Add(self, *a):
        self._n += 1
        return self._n - 1

    def GetImageCount(self):
        return self._n


class AcceleratorTable:
    def __init__(self, entries=()):
        pass


class AcceleratorEntry:
    def __init__(self, *a):
        pass


# ===================================================================== Sizer

class SizerItem:
    def __init__(self, obj, proportion=0, flag=0, border=0, userData=None):
        self.obj = obj
        self.proportion = proportion
        self.flag = flag
        self.border = border
        self.userData = userData
        self.shown = True

    def GetWindow(self):
        return self.obj if isinstance(self.obj, Window) else None

    def GetSizer(self):
        return self.obj if isinstance(self.obj, Sizer) else None

    def IsWindow(self):
        return isinstance(self.obj, Window)

    def IsSizer(self):
        return isinstance(self.obj, Sizer)

    def IsSpacer(self):
        return isinstance(self.obj, _Spacer)

    def GetProportion(self):
        return self.proportion

    def SetProportion(self, p):
        self.proportion = p

    def GetFlag(self):
        return self.flag

    def SetFlag(self, f):
        self.flag = f

    def GetBorder(self):
        return self.border

    def SetBorder(self, b):
        self.border = b

    def Show(self, show=True):
        self.shown = show


class SizerFlags:
    def __init__(self, proportion=0):
        self.proportion, self.flag, self.border = proportion, 0, 0

    def Expand(self):
        self.flag |= EXPAND
        return self

    def Proportion(self, p):
        self.proportion = p
        return self

    def Border(self, direction=ALL, borderInPixels=5):
        self.flag |= direction
        self.border = borderInPixels
        return self

    def DoubleBorder(self, direction=ALL):
        return self.Border(direction, 10)

    def Center(self):
        self.flag |= ALIGN_CENTER
        return self

    Centre = Center

    def Align(self, a):
        self.flag |= a
        return self

    def Left(self):
        return self

    Top = Left

    def Right(self):
        self.flag |= ALIGN_RIGHT
        return self

    def Bottom(self):
        self.flag |= ALIGN_BOTTOM
        return self

    def CenterVertical(self):
        self.flag |= ALIGN_CENTER_VERTICAL
        return self

    def CenterHorizontal(self):
        self.flag |= ALIGN_CENTER_HORIZONTAL
        return self

    def Shaped(self):
        return self

    def FixedMinSize(self):
        return self

    def ReserveSpaceEvenIfHidden(self):
        return self


class _Spacer:
    def __init__(self, w, h, stretch=0):
        self.w, self.h, self.stretch = w, h, stretch
        self._id = None


class Sizer:
    """Sizer は Android 側では「中身を並べる箱」になる。ウィンドウの一番上の Sizer はそのウィンドウ自身、
    入れ子の Sizer は目に見えない箱(frame)を作って、部品をその中へ移す。"""
    _mode = "box"

    def __init__(self, orient=VERTICAL):
        self._orient = orient
        self._items = []
        self._owner = None   # この Sizer を持つウィンドウ(部品の親)
        self._box_id = None  # Android 側の箱
        self._outer = None

    # ---- 追加

    def _to_item(self, obj, proportion=0, flag=0, border=0, userData=None):
        if isinstance(proportion, SizerFlags):
            proportion, flag, border = proportion.proportion, proportion.flag, proportion.border
        if isinstance(obj, (tuple, list, Size)):
            obj = _Spacer(obj[0], obj[1])
        return SizerItem(obj, proportion, flag, border, userData)

    def Add(self, obj, proportion=0, flag=0, border=0, userData=None):
        if isinstance(obj, int) and isinstance(proportion, int) and not isinstance(obj, bool) \
                and flag in (0,) and border == 0 and not isinstance(obj, Window):
            # Add(width, height) の形の空き
            obj = _Spacer(obj, proportion)
            proportion = 0
        it = self._to_item(obj, proportion, flag, border, userData)
        self._items.append(it)
        self._placed(it)
        return it

    def Insert(self, index, obj, proportion=0, flag=0, border=0, userData=None):
        it = self._to_item(obj, proportion, flag, border, userData)
        self._items.insert(index, it)
        self._placed(it)
        return it

    def Prepend(self, obj, proportion=0, flag=0, border=0, userData=None):
        return self.Insert(0, obj, proportion, flag, border, userData)

    def AddSpacer(self, size):
        return self.Add(_Spacer(size if self._orient == HORIZONTAL else 0,
                                size if self._orient == VERTICAL else 0))

    def AddStretchSpacer(self, prop=1):
        it = self._to_item(_Spacer(0, 0, prop), prop)
        self._items.append(it)
        self._placed(it)
        return it

    InsertSpacer = lambda self, index, size: self.AddSpacer(size)
    InsertStretchSpacer = lambda self, index, prop=1: self.AddStretchSpacer(prop)
    PrependSpacer = AddSpacer
    PrependStretchSpacer = AddStretchSpacer

    def AddMany(self, items):
        for it in items:
            if isinstance(it, (tuple, list)) and it and isinstance(it[0], (Window, Sizer)):
                self.Add(*it)
            else:
                self.Add(it)

    # ---- Android への反映

    def _container_id(self):
        return self._box_id if self._box_id is not None else (self._owner._id if self._owner else None)

    def _attach(self, owner, window_for_box=None):
        """window_for_box が None でなければ、そのウィンドウ自身をこの Sizer の箱にする。"""
        self._owner = owner
        b = _g.bridge()
        if window_for_box is not None:
            self._box_id = None
            b.send({"op": "set", "id": owner._id, "props": self._box_props()})
        else:
            if self._box_id is None:
                self._box_id = b.new_id()
                b.send({"op": "new", "id": self._box_id, "type": self._box_type(),
                        "parent": owner._content_id(), "props": self._box_props()})
        for it in self._items:
            self._placed(it)

    def _box_type(self):
        return "frame"

    def _box_props(self):
        return {"orient": "h" if self._orient == HORIZONTAL else "v"}

    def _child_opts(self, it):
        margin = _flag_margin(it.flag, it.border)
        if self._orient == HORIZONTAL:
            align = "end" if it.flag & ALIGN_BOTTOM else ("center" if it.flag & ALIGN_CENTER_VERTICAL else "start")
        else:
            align = "end" if it.flag & ALIGN_RIGHT else ("center" if it.flag & ALIGN_CENTER_HORIZONTAL else "start")
        return {"m": "box", "weight": float(it.proportion or 0), "expand": bool(it.flag & EXPAND),
                "margin": margin, "align": align}

    def _placed(self, it):
        if self._owner is None:
            return
        cid = self._container_id()
        b = _g.bridge()
        obj = it.obj
        if isinstance(obj, Window):
            obj._containing_sizer = self
            b.send({"op": "reparent", "id": obj._id, "parent": cid})
            obj._layout(self._child_opts(it))
        elif isinstance(obj, Sizer):
            obj._outer = self
            obj._attach(self._owner)
            b.send({"op": "reparent", "id": obj._box_id, "parent": cid})
            b.send({"op": "layout", "id": obj._box_id, "opts": self._child_opts(it)})
        elif isinstance(obj, _Spacer):
            if obj._id is None:
                obj._id = b.new_id()
                b.send({"op": "new", "id": obj._id, "type": "space", "parent": cid,
                        "props": {"width": obj.w, "height": obj.h}})
            else:
                b.send({"op": "reparent", "id": obj._id, "parent": cid})
            o = self._child_opts(it)
            o["weight"] = float(obj.stretch or it.proportion or 0)
            b.send({"op": "layout", "id": obj._id, "opts": o})
        self._send_order()

    def _item_ids(self):
        out = []
        for it in self._items:
            o = it.obj
            if isinstance(o, Window):
                out.append(o._id)
            elif isinstance(o, Sizer):
                out.append(o._box_id)
            elif isinstance(o, _Spacer):
                out.append(o._id)
        return [i for i in out if i is not None]

    def _send_order(self):
        cid = self._container_id()
        if cid is not None:
            _g.bridge().send({"op": "order", "id": cid, "ids": self._item_ids()})

    # ---- 操作

    def GetChildren(self):
        return list(self._items)

    def GetItemCount(self):
        return len(self._items)

    def GetItem(self, i):
        if isinstance(i, int):
            return self._items[i] if 0 <= i < len(self._items) else None
        for it in self._items:
            if it.obj is i:
                return it
        return None

    def Detach(self, obj):
        if isinstance(obj, int):
            it = self._items.pop(obj)
        else:
            it = self.GetItem(obj)
            if it is None:
                return False
            self._items.remove(it)
        if isinstance(it.obj, Window):
            it.obj._containing_sizer = None
            it.obj._layout({"m": "none"})
        self._send_order()
        return True

    def Remove(self, obj):
        return self.Detach(obj)

    def Clear(self, delete_windows=False):
        for it in list(self._items):
            if delete_windows and isinstance(it.obj, Window):
                it.obj.Destroy()
            else:
                self.Detach(it.obj)
        self._items = []

    def Replace(self, old, new, recursive=False):
        it = self.GetItem(old)
        if it is None:
            return False
        it.obj = new
        self._placed(it)
        return True

    def Show(self, obj, show=True, recursive=False):
        if isinstance(obj, bool):
            for it in self._items:
                if isinstance(it.obj, Window):
                    it.obj.Show(obj)
            return True
        if isinstance(obj, int):
            obj = self._items[obj].obj
        if isinstance(obj, Window):
            obj.Show(show)
        elif isinstance(obj, Sizer):
            obj.ShowItems(show)
        return True

    def Hide(self, obj, recursive=False):
        return self.Show(obj, False)

    def ShowItems(self, show):
        for it in self._items:
            self.Show(it.obj, show)

    def IsShown(self, obj):
        return True

    def Layout(self):
        pass

    def Fit(self, window):
        return window.GetSize() if window else Size(0, 0)

    FitInside = SetSizeHints = SetVirtualSizeHints = lambda self, *a: None

    def GetSize(self):
        return Size(0, 0)

    GetMinSize = CalcMin = GetSize

    def SetMinSize(self, *a):
        pass

    def SetDimension(self, *a):
        pass

    def GetOrientation(self):
        return self._orient

    def SetOrientation(self, o):
        self._orient = o

    def GetContainingWindow(self):
        return self._owner

    def SetItemMinSize(self, *a):
        pass

    def SetEmptyCellSize(self, s):
        pass

    def AddGrowableCol(self, idx, proportion=0):
        pass

    def AddGrowableRow(self, idx, proportion=0):
        pass

    def SetFlexibleDirection(self, d):
        pass

    def SetNonFlexibleGrowMode(self, m):
        pass

    def Destroy(self):
        pass


class BoxSizer(Sizer):
    def __init__(self, orient=HORIZONTAL):
        Sizer.__init__(self, orient)


class WrapSizer(BoxSizer):
    def __init__(self, orient=HORIZONTAL, flags=0):
        BoxSizer.__init__(self, orient)

    def _box_props(self):
        p = BoxSizer._box_props(self)
        p["wrap"] = True
        return p


class StaticBoxSizer(BoxSizer):
    def __init__(self, box, orient=HORIZONTAL, label=""):
        if isinstance(box, Window) and not isinstance(box, StaticBox):
            parent, box = box, None
        elif isinstance(box, int):
            orient, parent, box = box, orient, None
        else:
            parent = None
        BoxSizer.__init__(self, orient)
        self._label = label if box is None else box.GetLabel()
        self._sbox = box
        if box is not None:
            box.Hide()

    def _box_type(self):
        return "labelframe"

    def _box_props(self):
        p = BoxSizer._box_props(self)
        p["text"] = self._label
        return p

    def _attach(self, owner, window_for_box=None):
        # StaticBoxSizer はいつも自分の枠(ラベル付き)を作る
        Sizer._attach(self, owner, None)
        if window_for_box is not None:
            b = _g.bridge()
            b.send({"op": "set", "id": owner._id, "props": {"orient": "v"}})
            b.send({"op": "layout", "id": self._box_id,
                    "opts": {"m": "box", "weight": 1.0, "expand": True, "margin": [0, 0, 0, 0],
                             "align": "start"}})
            b.send({"op": "order", "id": owner._id, "ids": [self._box_id]})

    def GetStaticBox(self):
        if self._sbox is None:
            self._sbox = _FakeStaticBox(self)
        return self._sbox


class _FakeStaticBox:
    def __init__(self, sizer):
        self._sizer = sizer

    def __getattr__(self, name):
        owner = self._sizer._owner
        return getattr(owner, name)

    def SetLabel(self, t):
        self._sizer._label = t
        if self._sizer._box_id is not None:
            _g.bridge().send({"op": "set", "id": self._sizer._box_id, "props": {"text": t}})

    def GetLabel(self):
        return self._sizer._label


def _grid_args(args, kw):
    """GridSizer(rows, cols, vgap, hgap) / (cols, vgap, hgap) / (rows, cols, gap) / (cols, gap)"""
    rows, cols = kw.get("rows", 0), kw.get("cols", 0)
    vgap, hgap = kw.get("vgap", 0), kw.get("hgap", 0)
    gap = kw.get("gap")
    a = list(args)
    if a and isinstance(a[-1], (tuple, list, Size)):
        gap = a.pop()
    if len(a) >= 4:
        rows, cols, vgap, hgap = a[:4]
    elif len(a) == 3:
        if gap is not None:
            rows, cols = a[0], a[1]
        else:
            cols, vgap, hgap = a
    elif len(a) == 2:
        if gap is not None:
            rows, cols = a
        else:
            cols, vgap = a
    elif len(a) == 1:
        cols = a[0]
    if gap is not None:
        hgap, vgap = gap[0], gap[1]
    return rows, cols, vgap, hgap


class GridSizer(Sizer):
    def __init__(self, *args, **kw):
        rows, cols, vgap, hgap = _grid_args(args, kw)
        Sizer.__init__(self, VERTICAL)
        self._rows, self._cols, self._vgap, self._hgap = rows, cols, vgap, hgap
        self._grow_cols = {}
        self._grow_rows = {}
        self._equal = True

    def _ncols(self):
        if self._cols:
            return self._cols
        n = len(self._items)
        return max(1, -(-n // max(1, self._rows)))

    def _box_props(self):
        p = {"orient": "v"}
        ncols = self._ncols()
        cols = {str(i): [float(self._grow_cols.get(i, 1 if self._equal else 0)), 0, 0] for i in range(ncols)}
        p["cols"] = cols
        if self._grow_rows:
            p["rows"] = {str(i): [float(w), 0, 0] for i, w in self._grow_rows.items()}
        return p

    def _child_opts(self, it):
        i = self._items.index(it)
        ncols = self._ncols()
        r, c = divmod(i, ncols)
        sticky = ""
        if it.flag & EXPAND:
            sticky = "nsew"
        else:
            if it.flag & ALIGN_RIGHT:
                sticky += "e"
            elif not it.flag & ALIGN_CENTER_HORIZONTAL:
                sticky += "w"
            if it.flag & ALIGN_BOTTOM:
                sticky += "s"
            elif not it.flag & ALIGN_CENTER_VERTICAL:
                sticky += "n"
        m = _flag_margin(it.flag, it.border)
        return {"m": "grid", "row": r, "col": c, "sticky": sticky,
                "padx": [m[0] + (self._hgap / 2 if c else 0), m[2] + (self._hgap / 2 if c < ncols - 1 else 0)],
                "pady": [m[1] + (self._vgap / 2 if r else 0), m[3] + self._vgap / 2]}

    def _placed(self, it):
        Sizer._placed(self, it)
        cid = self._container_id()
        if cid is not None:
            _g.bridge().send({"op": "set", "id": cid, "props": self._box_props()})

    def SetCols(self, c):
        self._cols = c

    def SetRows(self, r):
        self._rows = r

    def GetCols(self):
        return self._ncols()

    def GetRows(self):
        return self._rows

    def SetVGap(self, g):
        self._vgap = g

    def SetHGap(self, g):
        self._hgap = g


class FlexGridSizer(GridSizer):
    def __init__(self, *args, **kw):
        GridSizer.__init__(self, *args, **kw)
        self._equal = False

    def AddGrowableCol(self, idx, proportion=1):
        self._grow_cols[idx] = proportion or 1
        cid = self._container_id()
        if cid is not None:
            _g.bridge().send({"op": "set", "id": cid, "props": self._box_props()})

    def AddGrowableRow(self, idx, proportion=1):
        self._grow_rows[idx] = proportion or 1
        cid = self._container_id()
        if cid is not None:
            _g.bridge().send({"op": "set", "id": cid, "props": self._box_props()})


class GridBagSizer(FlexGridSizer):
    def __init__(self, vgap=0, hgap=0):
        FlexGridSizer.__init__(self, 0, 0, vgap, hgap)
        self._cols = 0
        self._pos = {}

    def Add(self, obj, pos=(0, 0), span=(1, 1), flag=0, border=0, userData=None):
        if isinstance(obj, (tuple, list, Size)):
            obj = _Spacer(obj[0], obj[1])
        it = SizerItem(obj, 0, flag, border, userData)
        self._pos[id(it)] = (pos, span)
        self._items.append(it)
        self._placed(it)
        return it

    def _ncols(self):
        n = 1
        for it in self._items:
            (r, c), (rs, cs) = self._pos.get(id(it), ((0, 0), (1, 1)))
            n = max(n, c + cs)
        return n

    def _child_opts(self, it):
        o = GridSizer._child_opts(self, it)
        (r, c), (rs, cs) = self._pos.get(id(it), ((0, 0), (1, 1)))
        o.update({"row": r, "col": c, "rowspan": rs, "colspan": cs})
        return o

    def SetItemPosition(self, item, pos):
        it = self.GetItem(item)
        if it is not None:
            self._pos[id(it)] = (pos, self._pos.get(id(it), ((0, 0), (1, 1)))[1])
            self._placed(it)
        return True


class GBPosition(tuple):
    def __new__(cls, row=0, col=0):
        return tuple.__new__(cls, (row, col))


class GBSpan(tuple):
    def __new__(cls, rowspan=1, colspan=1):
        return tuple.__new__(cls, (rowspan, colspan))


class StdDialogButtonSizer(BoxSizer):
    def __init__(self):
        BoxSizer.__init__(self, HORIZONTAL)
        self._buttons = []

    def AddButton(self, button):
        self._buttons.append(button)

    def SetAffirmativeButton(self, b):
        self.AddButton(b)

    SetNegativeButton = SetCancelButton = SetAffirmativeButton

    def Realize(self):
        self.AddStretchSpacer()
        for b in self._buttons:
            self.Add(b, 0, ALL, 4)


# ===================================================================== 描画(DC)

class DC:
    def __init__(self, window=None):
        self._win = window
        self._pen = BLACK_PEN
        self._brush = WHITE_BRUSH
        self._font = Font(10)
        self._fg = BLACK
        self._bg_brush = None
        self._items = []
        self._owns = False
        if window is not None:
            if window._draw is None:
                window._draw = []
                self._owns = True
            self._items = window._draw

    def __enter__(self):
        return self

    def __exit__(self, *a):
        self._finish()
        return False

    def __del__(self):
        try:
            self._finish()
        except Exception:
            pass

    def _finish(self):
        if self._owns and self._win is not None and not self._win._destroyed:
            self._owns = False
            draw = self._win._draw
            self._win._draw = None
            self._win._send(draw=draw or [])

    def _stroke(self):
        if self._pen is None or self._pen.style == PENSTYLE_TRANSPARENT:
            return None, 0
        return _col(self._pen.colour), max(1, self._pen.width)

    def _fill(self):
        if self._brush is None or self._brush.style == BRUSHSTYLE_TRANSPARENT:
            return None
        return _col(self._brush.colour)

    def _add(self, d):
        self._items.append(d)

    # ---- 設定

    def SetPen(self, pen):
        self._pen = pen

    def SetBrush(self, brush):
        self._brush = brush

    def SetFont(self, font):
        self._font = font

    def SetTextForeground(self, c):
        self._fg = Colour(c)

    def SetTextBackground(self, c):
        pass

    def SetBackground(self, brush):
        self._bg_brush = brush

    def SetBackgroundMode(self, m):
        pass

    def SetLogicalFunction(self, f):
        pass

    def SetUserScale(self, x, y):
        pass

    def SetDeviceOrigin(self, x, y):
        pass

    def SetClippingRegion(self, *a):
        pass

    DestroyClippingRegion = lambda self: None

    def GetPen(self):
        return self._pen

    def GetBrush(self):
        return self._brush

    def GetFont(self):
        return self._font

    def GetSize(self):
        return self._win.GetSize() if self._win else Size(0, 0)

    GetSizeTuple = GetSize

    def GetTextExtent(self, text):
        f = self._font._airsky_font() if self._font else {"size": 13}
        size = f.get("size", 13)
        lines = str(text).split("\n")
        return Size(int(max(len(l) for l in lines) * size * 0.6), int(len(lines) * size * 1.25))

    GetFullTextExtent = GetMultiLineTextExtent = GetTextExtent

    def GetCharHeight(self):
        return self.GetTextExtent("M")[1]

    def GetCharWidth(self):
        return self.GetTextExtent("M")[0]

    def IsOk(self):
        return True

    # ---- 描く

    def Clear(self):
        del self._items[:]
        if self._bg_brush is not None and self._bg_brush.style != BRUSHSTYLE_TRANSPARENT:
            s = self.GetSize()
            self._add({"t": "rectangle", "c": [0, 0, max(s[0], 4000), max(s[1], 4000)],
                       "fill": _col(self._bg_brush.colour), "outline": None, "w": 0})

    def DrawLine(self, x1, y1=None, x2=None, y2=None):
        if y1 is None or (isinstance(x1, (tuple, list)) and isinstance(y1, (tuple, list))):
            (x1, y1), (x2, y2) = x1, y1
        c, w = self._stroke()
        if c:
            self._add({"t": "line", "c": [x1, y1, x2, y2], "fill": c, "w": w})

    def DrawLines(self, points, xoffset=0, yoffset=0):
        c, w = self._stroke()
        coords = []
        for p in points:
            coords += [p[0] + xoffset, p[1] + yoffset]
        if c and len(coords) >= 4:
            self._add({"t": "line", "c": coords, "fill": c, "w": w})

    def DrawPoint(self, x, y=None):
        if y is None:
            x, y = x
        c, w = self._stroke()
        if c:
            self._add({"t": "rectangle", "c": [x, y, x + 1, y + 1], "fill": c, "outline": None, "w": 0})

    def DrawRectangle(self, x, y=None, w=None, h=None):
        if y is None:
            if isinstance(x, Rect):
                x, y, w, h = x.x, x.y, x.width, x.height
            else:
                x, y, w, h = x
        elif w is None:
            (x, y), (w, h) = x, y
        c, sw = self._stroke()
        self._add({"t": "rectangle", "c": [x, y, x + w, y + h], "fill": self._fill(), "outline": c, "w": sw})

    def DrawRoundedRectangle(self, x, y=None, w=None, h=None, radius=None):
        if y is not None and w is None:
            radius = y
            x, y, w, h = x.x, x.y, x.width, x.height
        c, sw = self._stroke()
        self._add({"t": "rectangle", "c": [x, y, x + w, y + h], "fill": self._fill(), "outline": c,
                   "w": sw, "radius": radius or 0})

    def DrawCircle(self, x, y=None, radius=None):
        if radius is None:
            radius = y
            x, y = x
        c, sw = self._stroke()
        self._add({"t": "oval", "c": [x - radius, y - radius, x + radius, y + radius],
                   "fill": self._fill(), "outline": c, "w": sw})

    def DrawEllipse(self, x, y=None, w=None, h=None):
        if y is None:
            x, y, w, h = x.x, x.y, x.width, x.height
        c, sw = self._stroke()
        self._add({"t": "oval", "c": [x, y, x + w, y + h], "fill": self._fill(), "outline": c, "w": sw})

    def DrawPolygon(self, points, xoffset=0, yoffset=0, fill_style=0):
        coords = []
        for p in points:
            coords += [p[0] + xoffset, p[1] + yoffset]
        c, sw = self._stroke()
        self._add({"t": "polygon", "c": coords, "fill": self._fill(), "outline": c, "w": sw})

    def DrawArc(self, x1, y1, x2, y2, xc, yc):
        import math
        r = math.hypot(x1 - xc, y1 - yc)
        a1 = math.degrees(math.atan2(-(y1 - yc), x1 - xc))
        a2 = math.degrees(math.atan2(-(y2 - yc), x2 - xc))
        ext = (a2 - a1) % 360 or 360
        c, sw = self._stroke()
        self._add({"t": "arc", "c": [xc - r, yc - r, xc + r, yc + r], "start": a1, "extent": ext,
                   "style": "pieslice", "fill": self._fill(), "outline": c, "w": sw})

    def DrawEllipticArc(self, x, y, w, h, start, end):
        c, sw = self._stroke()
        self._add({"t": "arc", "c": [x, y, x + w, y + h], "start": start, "extent": end - start,
                   "style": "pieslice", "fill": self._fill(), "outline": c, "w": sw})

    def DrawText(self, text, x, y=None):
        if y is None:
            x, y = x
        self._add({"t": "text", "c": [x, y], "text": str(text), "fill": _col(self._fg),
                   "font": self._font._airsky_font() if self._font else None, "anchor": "nw"})

    def DrawRotatedText(self, text, x, y, angle):
        self._add({"t": "text", "c": [x, y], "text": str(text), "fill": _col(self._fg),
                   "font": self._font._airsky_font() if self._font else None, "anchor": "nw",
                   "angle": angle})

    def DrawLabel(self, text, rect, alignment=0, indexAccel=-1):
        self.DrawText(text, rect.x, rect.y)

    def DrawBitmap(self, bmp, x, y=None, useMask=False):
        if y is None:
            x, y = x
        ref = bmp._ref() if isinstance(bmp, Bitmap) else None
        if ref:
            self._add({"t": "image", "c": [x, y], "img": ref, "anchor": "nw"})

    DrawIcon = DrawBitmap

    def DrawCheckMark(self, *a):
        pass

    def GradientFillLinear(self, rect, initialColour, destColour, nDirection=RIGHT):
        self._add({"t": "rectangle", "c": [rect.x, rect.y, rect.x + rect.width, rect.y + rect.height],
                   "fill": _col(initialColour), "outline": None, "w": 0})

    def Blit(self, *a, **kw):
        return True

    def BeginDrawing(self):
        pass

    def EndDrawing(self):
        self._finish()


PaintDC = ClientDC = WindowDC = BufferedPaintDC = BufferedDC = AutoBufferedPaintDC = ScreenDC = DC


class GCDC(DC):
    def __init__(self, dc=None):
        DC.__init__(self, None)
        if isinstance(dc, DC):
            self._win = dc._win
            self._items = dc._items


class MemoryDC(DC):
    def __init__(self, bitmap=None):
        DC.__init__(self, None)

    def SelectObject(self, bmp):
        pass


class GraphicsContext:
    @staticmethod
    def Create(dc):
        return dc


# ===================================================================== ダイアログ

def _dialog_icon(style):
    if style & ICON_ERROR:
        return "error"
    if style & ICON_WARNING:
        return "warning"
    if style & ICON_QUESTION:
        return "question"
    return "info"


def MessageBox(message, caption="Message", style=OK | CENTRE, parent=None, x=-1, y=-1):
    keys, labels = _buttons_for(style)
    i = _g.bridge().dialog("message", title=str(caption or ""), message=str(message),
                           icon=_dialog_icon(style), buttons=labels)
    if not isinstance(i, int) or i < 0 or i >= len(keys):
        return CANCEL if style & CANCEL else (NO if style & NO else OK)
    return {"ok": OK, "yes": YES, "no": NO, "cancel": CANCEL}[keys[i]]


def _buttons_for(style):
    keys = []
    if style & YES:
        keys += ["yes", "no"]
    elif style & OK or not style & (YES | NO | CANCEL):
        keys.append("ok")
    if style & CANCEL:
        keys.append("cancel")
    labels = [{"ok": "OK", "yes": "はい", "no": "いいえ", "cancel": "キャンセル"}[k] for k in keys]
    return keys, labels


class MessageDialog:
    def __init__(self, parent, message, caption="Message", style=OK | CENTRE, pos=DefaultPosition):
        self._message, self._caption, self._style = message, caption, style
        self._ext = ""
        self._labels = {}

    def SetExtendedMessage(self, m):
        self._ext = m

    def SetMessage(self, m):
        self._message = m

    def SetYesNoLabels(self, yes, no):
        self._labels.update(yes=yes, no=no)
        return True

    def SetOKLabel(self, ok):
        self._labels["ok"] = ok
        return True

    def SetOKCancelLabels(self, ok, cancel):
        self._labels.update(ok=ok, cancel=cancel)
        return True

    def SetYesNoCancelLabels(self, yes, no, cancel):
        self._labels.update(yes=yes, no=no, cancel=cancel)
        return True

    def ShowModal(self):
        keys, labels = _buttons_for(self._style)
        labels = [self._labels.get(k, l) for k, l in zip(keys, labels)]
        msg = str(self._message) + ("\n\n" + self._ext if self._ext else "")
        i = _g.bridge().dialog("message", title=str(self._caption or ""), message=msg,
                               icon=_dialog_icon(self._style), buttons=labels)
        if not isinstance(i, int) or i < 0 or i >= len(keys):
            return ID_CANCEL if self._style & CANCEL else (ID_NO if self._style & NO else ID_OK)
        return {"ok": ID_OK, "yes": ID_YES, "no": ID_NO, "cancel": ID_CANCEL}[keys[i]]

    def Destroy(self):
        return True

    def __enter__(self):
        return self

    def __exit__(self, *a):
        return False


GenericMessageDialog = RichMessageDialog = MessageDialog


class TextEntryDialog:
    _password = False

    def __init__(self, parent, message, caption="Please enter text", value="", style=TEXTENTRY_DIALOG_STYLE,
                 pos=DefaultPosition):
        self._message, self._caption, self._value = message, caption, str(value)
        self._multi = bool(style & TE_MULTILINE)

    def ShowModal(self):
        v = _g.bridge().dialog("input", title=str(self._caption), message=str(self._message),
                               initial=self._value, password=self._password, multiline=self._multi)
        if v is None:
            return ID_CANCEL
        self._value = v
        return ID_OK

    def GetValue(self):
        return self._value

    def SetValue(self, v):
        self._value = str(v)

    def Destroy(self):
        return True

    def __enter__(self):
        return self

    def __exit__(self, *a):
        return False


class PasswordEntryDialog(TextEntryDialog):
    _password = True


class NumberEntryDialog(TextEntryDialog):
    def __init__(self, parent, message, prompt, caption, value, min, max, pos=DefaultPosition):
        TextEntryDialog.__init__(self, parent, message + ("\n" + prompt if prompt else ""), caption, str(value))
        self._min, self._max = min, max

    def ShowModal(self):
        while True:
            r = TextEntryDialog.ShowModal(self)
            if r != ID_OK:
                return r
            try:
                v = int(self._value)
                if self._min <= v <= self._max:
                    return r
            except ValueError:
                pass
            MessageBox("%d から %d までの整数を入力してください" % (self._min, self._max), "入力が正しくありません",
                       OK | ICON_WARNING)

    def GetValue(self):
        try:
            return int(self._value)
        except ValueError:
            return -1


def GetTextFromUser(message, caption="Input text", default_value="", parent=None, x=-1, y=-1, centre=True):
    v = _g.bridge().dialog("input", title=str(caption), message=str(message), initial=str(default_value))
    return v if v is not None else ""


def GetPasswordFromUser(message, caption="Input text", default_value="", parent=None, x=-1, y=-1, centre=True):
    v = _g.bridge().dialog("input", title=str(caption), message=str(message), initial=str(default_value),
                           password=True)
    return v if v is not None else ""


def GetNumberFromUser(message, prompt, caption, value, min=0, max=100, parent=None, pos=DefaultPosition):
    d = NumberEntryDialog(parent, message, prompt, caption, value, min, max)
    return d.GetValue() if d.ShowModal() == ID_OK else -1


class SingleChoiceDialog:
    def __init__(self, parent, message, caption, choices, style=CHOICEDLG_STYLE, pos=DefaultPosition):
        self._message, self._caption, self._choices = message, caption, [str(c) for c in choices]
        self._sel = 0

    def ShowModal(self):
        i = _g.bridge().dialog("choice", title=str(self._caption or self._message), items=self._choices,
                               checked=self._sel)
        if not isinstance(i, int) or i < 0:
            return ID_CANCEL
        self._sel = i
        return ID_OK

    def GetSelection(self):
        return self._sel

    def SetSelection(self, i):
        self._sel = i

    def GetStringSelection(self):
        return self._choices[self._sel] if self._choices else ""

    def Destroy(self):
        return True

    def __enter__(self):
        return self

    def __exit__(self, *a):
        return False


class MultiChoiceDialog(SingleChoiceDialog):
    def __init__(self, *a, **kw):
        SingleChoiceDialog.__init__(self, *a, **kw)
        self._sels = []

    def ShowModal(self):
        r = _g.bridge().dialog("multichoice", title=str(self._caption or self._message),
                               items=self._choices, checked=self._sels)
        if r is None:
            return ID_CANCEL
        self._sels = [int(x) for x in r]
        return ID_OK

    def GetSelections(self):
        return list(self._sels)

    def SetSelections(self, s):
        self._sels = list(s)


def GetSingleChoice(message, caption, aChoices, parent=None, *a, **kw):
    d = SingleChoiceDialog(parent, message, caption, aChoices)
    return d.GetStringSelection() if d.ShowModal() == ID_OK else ""


def GetSingleChoiceIndex(message, caption, aChoices, parent=None, *a, **kw):
    d = SingleChoiceDialog(parent, message, caption, aChoices)
    return d.GetSelection() if d.ShowModal() == ID_OK else -1


def _wildcard(wildcard):
    parts = str(wildcard or "").split("|")
    out = []
    for i in range(0, len(parts) - 1, 2):
        out.append([parts[i], parts[i + 1].split(";")])
    return out


class FileDialog:
    def __init__(self, parent, message="Choose a file", defaultDir="", defaultFile="", wildcard="*.*",
                 style=FD_DEFAULT_STYLE, pos=DefaultPosition, size=DefaultSize, name="filedlg"):
        self._message, self._dir, self._file = message, defaultDir or os.getcwd(), defaultFile
        self._wildcard, self._style = wildcard, style
        self._paths = []

    def ShowModal(self):
        b = _g.bridge()
        if self._style & FD_SAVE:
            r = b.dialog("save", title=str(self._message), initialfile=self._file,
                         initialdir=os.path.abspath(self._dir), filetypes=_wildcard(self._wildcard))
            self._paths = [r] if r else []
        else:
            r = b.dialog("open", title=str(self._message), multiple=bool(self._style & FD_MULTIPLE),
                         initialdir=os.path.abspath(self._dir), filetypes=_wildcard(self._wildcard))
            self._paths = list(r or [])
        return ID_OK if self._paths else ID_CANCEL

    def GetPath(self):
        return self._paths[0] if self._paths else ""

    def GetPaths(self):
        return list(self._paths)

    def GetFilename(self):
        return os.path.basename(self.GetPath())

    def GetFilenames(self):
        return [os.path.basename(p) for p in self._paths]

    def GetDirectory(self):
        return os.path.dirname(self.GetPath()) or self._dir

    def SetDirectory(self, d):
        self._dir = d

    def SetFilename(self, f):
        self._file = f

    def SetWildcard(self, w):
        self._wildcard = w

    def GetFilterIndex(self):
        return 0

    def SetFilterIndex(self, i):
        pass

    def Destroy(self):
        return True

    def __enter__(self):
        return self

    def __exit__(self, *a):
        return False


class DirDialog:
    def __init__(self, parent, message="Choose a directory", defaultPath="", style=DD_DEFAULT_STYLE,
                 pos=DefaultPosition, size=DefaultSize, name="dirdlg"):
        self._message, self._path = message, defaultPath or os.getcwd()

    def ShowModal(self):
        r = _g.bridge().dialog("open", title=str(self._message), directory=True,
                               initialdir=os.path.abspath(self._path))
        if not r:
            return ID_CANCEL
        self._path = r[0]
        return ID_OK

    def GetPath(self):
        return self._path

    def SetPath(self, p):
        self._path = p

    def Destroy(self):
        return True

    def __enter__(self):
        return self

    def __exit__(self, *a):
        return False


def FileSelector(message="Select a file", default_path="", default_filename="", default_extension="",
                 wildcard="*.*", flags=0, parent=None, x=-1, y=-1):
    d = FileDialog(parent, message, default_path, default_filename, wildcard, flags)
    return d.GetPath() if d.ShowModal() == ID_OK else ""


def DirSelector(message="Select a directory", default_path="", style=0, pos=DefaultPosition, parent=None):
    d = DirDialog(parent, message, default_path)
    return d.GetPath() if d.ShowModal() == ID_OK else ""


class ColourData:
    def __init__(self):
        self._c = BLACK

    def GetColour(self):
        return self._c

    def SetColour(self, c):
        self._c = Colour(c)

    def SetChooseFull(self, f):
        pass


class ColourDialog:
    def __init__(self, parent, data=None):
        self._data = data or ColourData()

    def ShowModal(self):
        r = _g.bridge().dialog("color", title="", initial=_col(self._data._c))
        if not r:
            return ID_CANCEL
        self._data._c = Colour(r)
        return ID_OK

    def GetColourData(self):
        return self._data

    def Destroy(self):
        return True


def GetColourFromUser(parent=None, colInit=None, caption=""):
    r = _g.bridge().dialog("color", title=caption, initial=_col(colInit) if colInit else None)
    return Colour(r) if r else NullColour


class FontDialog:
    def __init__(self, parent, data=None):
        self._data = data or FontData()

    def ShowModal(self):
        return ID_CANCEL

    def GetFontData(self):
        return self._data

    def Destroy(self):
        return True


class FontData:
    def __init__(self):
        self._font = Font(10)
        self._colour = BLACK

    def GetChosenFont(self):
        return self._font

    def SetInitialFont(self, f):
        self._font = f

    def GetColour(self):
        return self._colour

    def SetColour(self, c):
        self._colour = c


class ProgressDialog:
    def __init__(self, title, message, maximum=100, parent=None, style=0):
        self._dlg = Dialog(parent, title=title)
        self._text = StaticText(self._dlg, label=message)
        self._gauge = Gauge(self._dlg, range=maximum)
        s = BoxSizer(VERTICAL)
        s.Add(self._text, 0, ALL | EXPAND, 12)
        s.Add(self._gauge, 0, ALL | EXPAND, 12)
        self._dlg.SetSizer(s)
        self._dlg.Show()

    def Update(self, value, newmsg=""):
        self._gauge.SetValue(value)
        if newmsg:
            self._text.SetLabel(newmsg)
        _g.bridge().process_pending()
        return (True, False)

    def Pulse(self, newmsg=""):
        self._gauge.Pulse()
        if newmsg:
            self._text.SetLabel(newmsg)
        _g.bridge().process_pending()
        return (True, False)

    def Destroy(self):
        self._dlg.Destroy()
        return True

    def WasCancelled(self):
        return False


class AboutDialogInfo:
    def __init__(self):
        self._d = {}

    def __getattr__(self, name):
        if name.startswith("Set") or name.startswith("Add"):
            def setter(v, _n=name[3:]):
                self._d[_n] = v
            return setter
        raise AttributeError(name)


def AboutBox(info, parent=None):
    d = info._d
    lines = [str(d.get("Name", "")) + " " + str(d.get("Version", ""))]
    for k in ("Description", "Copyright", "WebSite", "Developers"):
        if d.get(k):
            lines.append(str(d[k]))
    MessageBox("\n\n".join(lines), "情報", OK | ICON_INFORMATION)


class TextDataObject:
    def __init__(self, text=""):
        self._text = text

    def GetText(self):
        return self._text

    def SetText(self, t):
        self._text = t


class _Clipboard:
    def Open(self):
        return True

    def Close(self):
        pass

    def IsOpened(self):
        return True

    def SetData(self, data):
        _g.bridge().send({"op": "clipboard", "text": data.GetText()})
        return True

    def GetData(self, data):
        v = _g.bridge().request({"op": "clipboard_get"})
        data.SetText(v or "")
        return v is not None

    def IsSupported(self, fmt):
        return True

    def Flush(self):
        return True

    def Clear(self):
        pass

    def __enter__(self):
        return self

    def __exit__(self, *a):
        return False


TheClipboard = _Clipboard()


class DataFormat:
    def __init__(self, f=0):
        pass


DF_TEXT = DF_UNICODETEXT = 1


class Config:
    """wx.Config(設定の保存)。プログラムの作業フォルダの .wxconfig に JSON で保存する。"""

    def __init__(self, appName="", vendorName="", localFilename="", globalFilename="", style=0):
        import json
        self._json = json
        self._path = os.path.join(os.getcwd(), ".%s.wxconfig" % (appName or "app"))
        try:
            with open(self._path, encoding="utf-8") as f:
                self._d = json.load(f)
        except (OSError, ValueError):
            self._d = {}

    def _save(self):
        with open(self._path, "w", encoding="utf-8") as f:
            self._json.dump(self._d, f, ensure_ascii=False, indent=1)

    def Read(self, key, defaultVal=""):
        return str(self._d.get(key, defaultVal))

    def ReadInt(self, key, defaultVal=0):
        try:
            return int(self._d.get(key, defaultVal))
        except (TypeError, ValueError):
            return defaultVal

    def ReadFloat(self, key, defaultVal=0.0):
        try:
            return float(self._d.get(key, defaultVal))
        except (TypeError, ValueError):
            return defaultVal

    def ReadBool(self, key, defaultVal=False):
        return bool(self._d.get(key, defaultVal))

    def Write(self, key, value):
        self._d[key] = value
        self._save()
        return True

    WriteInt = WriteFloat = WriteBool = Write

    def Exists(self, key):
        return key in self._d

    HasEntry = Exists

    def DeleteEntry(self, key, deleteGroupIfEmpty=True):
        self._d.pop(key, None)
        self._save()
        return True

    def DeleteAll(self):
        self._d = {}
        self._save()
        return True

    def Flush(self, currentOnly=False):
        self._save()
        return True

    def SetPath(self, p):
        pass

    @staticmethod
    def Get(createOnDemand=True):
        return Config()

    @staticmethod
    def Set(c):
        return c


FileConfig = ConfigBase = Config


class StandardPaths:
    @staticmethod
    def Get():
        return StandardPaths()

    def GetUserDataDir(self):
        return os.getcwd()

    GetDocumentsDir = GetUserConfigDir = GetDataDir = GetConfigDir = GetAppDocumentsDir = GetUserDataDir

    def GetTempDir(self):
        return os.environ.get("TMPDIR", "/tmp")

    def GetExecutablePath(self):
        return sys.argv[0]


def FindWindowById(id, parent=None):
    for w in _tops:
        if w._wid == id:
            return w
        r = w.FindWindowById(id)
        if r:
            return r
    return None


def GetTopLevelWindows():
    return [w for w in _tops if not w._destroyed]


def GetActiveWindow():
    tops = GetTopLevelWindows()
    return tops[-1] if tops else None


def IsBusy():
    return False


def GetKeyState(key):
    return False


def GetLocale():
    return None


class Locale:
    def __init__(self, *a, **kw):
        pass

    @staticmethod
    def GetSystemLanguage():
        return 0


LANGUAGE_DEFAULT = 1
LANGUAGE_JAPANESE = 160
LANGUAGE_ENGLISH = 57


def GetTranslation(s, *a):
    return s


_ = GetTranslation
