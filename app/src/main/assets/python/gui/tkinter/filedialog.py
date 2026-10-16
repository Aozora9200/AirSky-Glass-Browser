"""tkinter.filedialog の互換。

Android ではプログラムが端末のファイルを直接開けないので、プログラムの作業フォルダの中から選ぶか、
端末のファイルを作業フォルダへ取り込んでから、そのパスを返す。保存も作業フォルダの中に作る。
"""
import os

import _airsky_gui as _g


def _types(filetypes):
    out = []
    for ft in filetypes or ():
        try:
            label, pat = ft
        except (TypeError, ValueError):
            continue
        pats = pat if isinstance(pat, (tuple, list)) else str(pat).split()
        out.append([str(label), [str(p) for p in pats]])
    return out


def _initialdir(options):
    d = options.get("initialdir")
    return os.path.abspath(d) if d else os.getcwd()


def askopenfilename(**options):
    r = _g.bridge().dialog("open", title=str(options.get("title", "")), multiple=False,
                           filetypes=_types(options.get("filetypes")), initialdir=_initialdir(options))
    return r[0] if r else ""


def askopenfilenames(**options):
    r = _g.bridge().dialog("open", title=str(options.get("title", "")), multiple=True,
                           filetypes=_types(options.get("filetypes")), initialdir=_initialdir(options))
    return tuple(r or ())


def asksaveasfilename(**options):
    ext = options.get("defaultextension") or ""
    r = _g.bridge().dialog("save", title=str(options.get("title", "")),
                           initialfile=str(options.get("initialfile") or ""),
                           initialdir=_initialdir(options), defaultextension=str(ext),
                           filetypes=_types(options.get("filetypes")))
    if not r:
        return ""
    if ext and not os.path.splitext(r)[1]:
        r += ext if ext.startswith(".") else "." + ext
    return r


def askdirectory(**options):
    r = _g.bridge().dialog("open", title=str(options.get("title", "")), directory=True,
                           initialdir=_initialdir(options))
    return r[0] if r else ""


def askopenfile(mode="r", **options):
    name = askopenfilename(**options)
    return open(name, mode) if name else None


def askopenfiles(mode="r", **options):
    return [open(n, mode) for n in askopenfilenames(**options)]


def asksaveasfile(mode="w", **options):
    name = asksaveasfilename(**options)
    return open(name, mode) if name else None


class FileDialog:
    def __init__(self, master, title=None):
        self.title = title

    def go(self, dir_or_file=os.curdir, pattern="*", default="", key=None):
        return askopenfilename(title=self.title, initialdir=dir_or_file)


class LoadFileDialog(FileDialog):
    pass


class SaveFileDialog(FileDialog):
    def go(self, dir_or_file=os.curdir, pattern="*", default="", key=None):
        return asksaveasfilename(title=self.title, initialdir=dir_or_file, initialfile=default)


class Open:
    def __init__(self, master=None, **options):
        self.options = options

    def show(self, **options):
        return askopenfilename(**dict(self.options, **options))


class SaveAs(Open):
    def show(self, **options):
        return asksaveasfilename(**dict(self.options, **options))


class Directory(Open):
    def show(self, **options):
        return askdirectory(**dict(self.options, **options))
