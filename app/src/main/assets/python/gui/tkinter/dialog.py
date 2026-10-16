from tkinter.simpledialog import SimpleDialog

DIALOG_ICON = "questhead"


class Dialog:
    def __init__(self, master=None, cnf={}, **kw):
        kw = dict(cnf, **kw)
        d = SimpleDialog(master, text=kw.get("text", ""), buttons=list(kw.get("strings", ("OK",))),
                         title=kw.get("title"))
        self.num = d.go()
