"""tkinter.simpledialog の互換。"""
import _airsky_gui as _g
from tkinter import Toplevel, Frame, Button, messagebox


def _ask(kind, title, prompt, initialvalue=None, minvalue=None, maxvalue=None, show=None):
    while True:
        v = _g.bridge().dialog("input", title=str(title or ""), message=str(prompt or ""),
                               initial="" if initialvalue is None else str(initialvalue),
                               password=bool(show), number=kind)
        if v is None:
            return None
        try:
            if kind == "int":
                r = int(v)
            elif kind == "float":
                r = float(v)
            else:
                return v
        except ValueError:
            messagebox.showwarning("入力が正しくありません",
                                   "整数を入力してください" if kind == "int" else "数を入力してください")
            continue
        if minvalue is not None and r < minvalue:
            messagebox.showwarning("入力が小さすぎます", "%s 以上の値を入力してください" % minvalue)
            continue
        if maxvalue is not None and r > maxvalue:
            messagebox.showwarning("入力が大きすぎます", "%s 以下の値を入力してください" % maxvalue)
            continue
        return r


def askstring(title, prompt, **kw):
    return _ask("text", title, prompt, kw.get("initialvalue"), show=kw.get("show"))


def askinteger(title, prompt, **kw):
    return _ask("int", title, prompt, kw.get("initialvalue"), kw.get("minvalue"), kw.get("maxvalue"))


def askfloat(title, prompt, **kw):
    return _ask("float", title, prompt, kw.get("initialvalue"), kw.get("minvalue"), kw.get("maxvalue"))


class SimpleDialog:
    def __init__(self, master, text="", buttons=[], default=None, cancel=None, title=None, class_=None):
        self.text, self.buttons, self.default, self.cancel, self.title = text, buttons, default, cancel, title

    def go(self):
        i = _g.bridge().dialog("message", title=str(self.title or ""), message=str(self.text),
                               icon="question", buttons=[str(b) for b in self.buttons])
        return self.cancel if not isinstance(i, int) or i < 0 else i


class Dialog(Toplevel):
    """自分で body / buttonbox を作るダイアログ(simpledialog.Dialog を継承して使う形)。"""

    def __init__(self, parent, title=None):
        Toplevel.__init__(self, parent)
        if title:
            self.title(title)
        self.parent = parent
        self.result = None
        body = Frame(self)
        self.initial_focus = self.body(body)
        body.pack(padx=5, pady=5, fill="both", expand=True)
        self.buttonbox()
        self.protocol("WM_DELETE_WINDOW", self.cancel)
        if self.initial_focus:
            self.initial_focus.focus_set()
        self.wait_window(self)

    def body(self, master):
        pass

    def buttonbox(self):
        box = Frame(self)
        Button(box, text="OK", width=10, command=self.ok).pack(side="left", padx=5, pady=5)
        Button(box, text="キャンセル", width=10, command=self.cancel).pack(side="left", padx=5, pady=5)
        box.pack()

    def ok(self, event=None):
        if not self.validate():
            return
        self.withdraw()
        self.apply()
        self.cancel()

    def cancel(self, event=None):
        self.destroy()

    def validate(self):
        return 1

    def apply(self):
        pass
