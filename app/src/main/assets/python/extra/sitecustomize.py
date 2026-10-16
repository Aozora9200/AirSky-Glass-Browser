# AirSky Browser の Python 実行ツールが、プログラムの前に読み込む設定。
#  - webbrowser.open() で、AirSky Browser の新しいタブにページを開く
#  - os.startfile が無い環境でも、ファイルを開く操作を AirSky に頼めるようにする
import os
import sys


def _install_webbrowser():
    try:
        import webbrowser
    except Exception:
        return

    class AirSkyBrowser(webbrowser.BaseBrowser):
        def open(self, url, new=0, autoraise=True):
            try:
                import _airsky_gui
                b = _airsky_gui.bridge()
                b.send({"op": "open_url", "url": str(url)})
                b.flush()
                return True
            except Exception:
                sys.stderr.write("ブラウザで開けませんでした: %s\n" % url)
                return False

    try:
        webbrowser.register("airsky", AirSkyBrowser, preferred=True)
    except TypeError:
        webbrowser.register("airsky", AirSkyBrowser)


if os.environ.get("AIRSKY_GUI_PORT"):
    _install_webbrowser()
