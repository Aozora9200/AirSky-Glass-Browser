"""tkinter.scrolledtext の互換(Android の文字欄はもともとスクロールできる)。"""
from tkinter import Frame, Text, Scrollbar
from tkinter.constants import LEFT, BOTH

__all__ = ["ScrolledText"]


class ScrolledText(Text):
    def __init__(self, master=None, **kw):
        self.frame = Frame(master)
        self.vbar = Scrollbar(self.frame)
        Text.__init__(self, self.frame, **kw)
        Text.pack(self, side=LEFT, fill=BOTH, expand=True)
        for m in ("pack", "grid", "place", "pack_forget", "grid_forget", "place_forget",
                  "pack_configure", "grid_configure", "place_configure", "pack_info", "grid_info"):
            setattr(self, m, getattr(self.frame, m))

    def __str__(self):
        return str(self.frame)
