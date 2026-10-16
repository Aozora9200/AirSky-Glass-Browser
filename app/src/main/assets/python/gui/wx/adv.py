from wx import AboutBox, AboutDialogInfo, HyperlinkCtrl, EVT_HYPERLINK  # noqa: F401

TaskBarIcon = None


class NotificationMessage:
    def __init__(self, title="", message="", parent=None, flags=0):
        self._t, self._m = title, message

    def SetTitle(self, t):
        self._t = t

    def SetMessage(self, m):
        self._m = m

    def Show(self, timeout=-1):
        import _airsky_gui as _g
        _g.bridge().toast("%s
%s" % (self._t, self._m) if self._t else self._m)
        return True
