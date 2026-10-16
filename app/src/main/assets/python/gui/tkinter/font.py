"""tkinter.font の互換。"""
import itertools

import _airsky_gui as _g

NORMAL = "normal"
ROMAN = "roman"
BOLD = "bold"
ITALIC = "italic"

_fonts = {}
_count = itertools.count(1)


def nametofont(name, root=None):
    if name in _fonts:
        return _fonts[name]
    return Font(name=name, family="TkDefaultFont", size=10)


class Font:
    def __init__(self, root=None, font=None, name=None, exists=False, **options):
        self.name = name or "font%d" % next(_count)
        self._o = {"family": "TkDefaultFont", "size": 10, "weight": NORMAL, "slant": ROMAN,
                   "underline": 0, "overstrike": 0}
        if font is not None:
            f = _g.font(font) or {}
            if isinstance(font, (tuple, list)) and font:
                self._o["family"] = font[0]
            elif font:
                self._o["family"] = str(font).split()[0]
            if f.get("size"):
                self._o["size"] = round(f["size"] / 1.333)
            if f.get("bold"):
                self._o["weight"] = BOLD
            if f.get("italic"):
                self._o["slant"] = ITALIC
        self._o.update(options)
        _fonts[self.name] = self

    def __str__(self):
        return self.name

    def _airsky_font(self):
        styles = []
        if self._o.get("weight") == BOLD:
            styles.append("bold")
        if self._o.get("slant") == ITALIC:
            styles.append("italic")
        if self._o.get("underline"):
            styles.append("underline")
        if self._o.get("overstrike"):
            styles.append("overstrike")
        return _g.font((self._o.get("family"), self._o.get("size", 10)) + tuple(styles))

    def actual(self, option=None, displayof=None):
        return self._o.get(option) if option else dict(self._o)

    def cget(self, option):
        return self._o.get(option)

    __getitem__ = cget

    def config(self, **options):
        if not options:
            return dict(self._o)
        self._o.update(options)

    configure = config

    def __setitem__(self, key, value):
        self._o[key] = value

    def copy(self):
        return Font(**self._o)

    def _px(self):
        s = int(self._o.get("size", 10))
        return s * 1.333 if s > 0 else -s

    def measure(self, text, displayof=None):
        w = 0.0
        for ch in str(text):
            w += self._px() * (1.0 if ord(ch) > 0x2E80 else 0.6)
        return int(w)

    def metrics(self, *options, **kw):
        px = self._px()
        fam = str(self._o.get("family", "")).lower()
        m = {"ascent": int(px * 0.8), "descent": int(px * 0.25), "linespace": int(px * 1.25),
             "fixed": 1 if ("mono" in fam or "courier" in fam) else 0}
        if options:
            return m.get(options[0])
        return m


def families(root=None, displayof=None):
    return ("TkDefaultFont", "Helvetica", "Arial", "Times", "Courier", "Noto Sans", "Noto Serif",
            "Noto Sans Mono", "Roboto", "sans-serif", "serif", "monospace")


def names(root=None):
    return tuple(_fonts)
