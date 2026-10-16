"""tkinter.colorchooser の互換。"""
import _airsky_gui as _g


def askcolor(color=None, **options):
    initial = _g.color(color) if color else None
    r = _g.bridge().dialog("color", title=str(options.get("title", "")), initial=initial)
    if not r:
        return (None, None)
    h = str(r)[-6:]
    return ((int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)), "#" + h)


class Chooser:
    def __init__(self, master=None, **options):
        self.options = options

    def show(self, color=None, **options):
        return askcolor(color, **dict(self.options, **options))
