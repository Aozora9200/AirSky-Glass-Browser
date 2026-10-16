"""tkinter.messagebox の互換(Android のダイアログで出す)。"""
import _airsky_gui as _g

ERROR = "error"
INFO = "info"
QUESTION = "question"
WARNING = "warning"
ABORTRETRYIGNORE = "abortretryignore"
OK = "ok"
OKCANCEL = "okcancel"
RETRYCANCEL = "retrycancel"
YESNO = "yesno"
YESNOCANCEL = "yesnocancel"
ABORT = "abort"
RETRY = "retry"
IGNORE = "ignore"
CANCEL = "cancel"
YES = "yes"
NO = "no"

_LABELS = {"ok": "OK", "cancel": "キャンセル", "yes": "はい", "no": "いいえ", "retry": "再試行",
           "abort": "中止", "ignore": "無視"}
_TYPES = {OK: ["ok"], OKCANCEL: ["ok", "cancel"], YESNO: ["yes", "no"],
          YESNOCANCEL: ["yes", "no", "cancel"], RETRYCANCEL: ["retry", "cancel"],
          ABORTRETRYIGNORE: ["abort", "retry", "ignore"]}


class Message:
    command = ""

    def __init__(self, master=None, **options):
        self.master = master
        self.options = options

    def show(self, **options):
        o = dict(self.options, **options)
        return _show(o.get("title"), o.get("message", ""), o.get("icon", INFO), o.get("type", OK),
                     o.get("detail"))


def _show(title, message, icon, type_, detail=None):
    keys = _TYPES.get(type_, ["ok"])
    msg = str(message or "")
    if detail:
        msg += "\n\n" + str(detail)
    i = _g.bridge().dialog("message", title=str(title or ""), message=msg, icon=icon,
                           buttons=[_LABELS[k] for k in keys])
    if not isinstance(i, int) or i < 0 or i >= len(keys):
        return "cancel" if "cancel" in keys else ("no" if "no" in keys else keys[0])
    return keys[i]


def showinfo(title=None, message=None, **options):
    return _show(title, message, INFO, OK, options.get("detail"))


def showwarning(title=None, message=None, **options):
    return _show(title, message, WARNING, OK, options.get("detail"))


def showerror(title=None, message=None, **options):
    return _show(title, message, ERROR, OK, options.get("detail"))


def askquestion(title=None, message=None, **options):
    return _show(title, message, QUESTION, options.get("type", YESNO), options.get("detail"))


def askokcancel(title=None, message=None, **options):
    return _show(title, message, QUESTION, OKCANCEL, options.get("detail")) == OK


def askyesno(title=None, message=None, **options):
    return _show(title, message, QUESTION, YESNO, options.get("detail")) == YES


def askyesnocancel(title=None, message=None, **options):
    r = _show(title, message, QUESTION, YESNOCANCEL, options.get("detail"))
    if r == CANCEL:
        return None
    return r == YES


def askretrycancel(title=None, message=None, **options):
    return _show(title, message, WARNING, RETRYCANCEL, options.get("detail")) == RETRY
