"""
ヘルプ画面(HelpCenterActivity)で使う Lottie アニメーションを生成する。

    python tools/gen_help_lottie.py

app/src/main/assets/help/*.json に書き出す。どれもスマホの画面を模した簡単な図形だけで作っていて、
フォントや画像は使わない。
"""
import json
import os

OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "help")
W, H, FR, OP = 360, 240, 60, 240

# ---- 色(Pixel 風のやわらかい配色) ----
def rgb(h, a=1.0):
    h = h.lstrip("#")
    return [int(h[0:2], 16) / 255, int(h[2:4], 16) / 255, int(h[4:6], 16) / 255, a]

PHONE = rgb("#1F2430")
SCREEN = rgb("#F8F9FF")
BAR = rgb("#E3E8F7")
LINE = rgb("#D4DAE8")
LINE2 = rgb("#E6EAF3")
ACCENT = rgb("#0B57D0")
ACCENT_SOFT = rgb("#D3E3FD")
GREEN = rgb("#34A853")
PINK = rgb("#F3A6C8")
YELLOW = rgb("#FBBC04")
FINGER = rgb("#5F6368")
WHITE = rgb("#FFFFFF")
DARK = rgb("#2B2F38")

EASE = {"o": {"x": [0.3], "y": [0]}, "i": {"x": [0.0], "y": [1]}}       # emphasized
LINEAR = {"o": {"x": [0.0], "y": [0]}, "i": {"x": [1.0], "y": [1]}}
STD = {"o": {"x": [0.4], "y": [0]}, "i": {"x": [0.2], "y": [1]}}
HOLD = "hold"


def st(v):
    return {"a": 0, "k": v}


def an(keys, ease=EASE):
    """keys: [(frame, value), ...] value はスカラーかリスト。"""
    out = []
    for i, (t, v) in enumerate(keys):
        v = v if isinstance(v, list) else [v]
        k = {"t": t, "s": v}
        if i < len(keys) - 1:
            if ease == HOLD:
                k["h"] = 1
            else:
                k.update({"o": ease["o"], "i": ease["i"]})
        out.append(k)
    return {"a": 1, "k": out}


def prop(v):
    return v if isinstance(v, dict) else st(v)


def tr(p=(0, 0), s=(100, 100), r=0, o=100, a=(0, 0)):
    return {"ty": "tr", "p": prop(list(p) if isinstance(p, tuple) else p),
            "a": prop(list(a) if isinstance(a, tuple) else a),
            "s": prop(list(s) if isinstance(s, tuple) else s),
            "r": prop(r), "o": prop(o), "sk": st(0), "sa": st(0)}


def rect(w, h, r, p=(0, 0)):
    return {"ty": "rc", "d": 1, "p": prop(list(p) if isinstance(p, tuple) else p),
            "s": prop([w, h] if not isinstance(w, dict) else w), "r": prop(r)}


def ellipse(d, p=(0, 0)):
    return {"ty": "el", "d": 1, "p": prop(list(p) if isinstance(p, tuple) else p),
            "s": prop([d, d] if not isinstance(d, dict) else d)}


def path(points, closed=False):
    z = [[0, 0] for _ in points]
    return {"ty": "sh", "ks": st({"i": z, "o": z, "v": [list(p) for p in points], "c": closed})}


def fill(c, o=100):
    return {"ty": "fl", "c": st(c), "o": prop(o), "r": 1}


def stroke(c, w, o=100):
    return {"ty": "st", "c": st(c), "o": prop(o), "w": prop(w), "lc": 2, "lj": 2, "ml": 4}


def trim(end, start=0, offset=0):
    return {"ty": "tm", "s": prop(start), "e": prop(end), "o": prop(offset), "m": 1}


def group(items, t=None, nm="g"):
    return {"ty": "gr", "nm": nm, "it": items + [t or tr()]}


_ind = [0]


def layer(nm, shapes, p=(W / 2, H / 2), s=(100, 100), o=100, r=0, a=(0, 0), mask=None):
    _ind[0] += 1
    L = {"ddd": 0, "ind": _ind[0], "ty": 4, "nm": nm, "sr": 1,
         "ks": {"o": prop(o), "r": prop(r),
                "p": prop(list(p) + [0] if isinstance(p, tuple) else p),
                "a": prop(list(a) + [0] if isinstance(a, tuple) else a),
                "s": prop(list(s) + [100] if isinstance(s, tuple) else s)},
         "ao": 0, "shapes": shapes, "ip": 0, "op": OP, "st": 0, "bm": 0}
    if mask:
        x, y, w, h = mask
        # マスクはレイヤーの座標系なので、レイヤーの位置の分だけずらす
        if isinstance(p, tuple):
            x, y = x - p[0], y - p[1]
        L["hasMask"] = True
        L["masksProperties"] = [{"inv": False, "mode": "a", "o": st(100), "x": st(0), "nm": "m",
                                 "pt": st({"i": [[0, 0]] * 4, "o": [[0, 0]] * 4, "c": True,
                                           "v": [[x, y], [x + w, y], [x + w, y + h], [x, y + h]]})}]
    return L


def doc(name, layers):
    return {"v": "5.7.4", "fr": FR, "ip": 0, "op": OP, "w": W, "h": H, "nm": name,
            "ddd": 0, "assets": [], "layers": layers}


# ---- 共通の部品 ----
CX, CY = 180, 120
PW, PH = 136, 216
SW, SH = 124, 204
SL, ST = CX - SW / 2, CY - SH / 2          # 画面の左上
SR, SB = SL + SW, ST + SH
SCREEN_MASK = (SL, ST, SW, SH)


def phone():
    return layer("phone", [
        group([rect(SW, SH, 15), fill(SCREEN)], nm="screen"),
        group([rect(PW, PH, 22), fill(PHONE)], nm="body"),
    ], p=(CX, CY))


def address_bar(y=ST + 16, o=100):
    return group([
        group([ellipse(7), fill(GREEN)], tr(p=(-SW / 2 + 22, 0))),
        group([rect(52, 4, 2), fill(LINE)], tr(p=(-6, 0))),
        group([rect(SW - 16, 18, 9), fill(BAR)]),
    ], tr(p=(CX, y), o=o), nm="address")


def bottom_bar(y=SB - 14, o=100, counter_scale=None):
    icons = []
    for i, x in enumerate([-44, -22, 22, 44]):
        icons.append(group([ellipse(6), fill(LINE)], tr(p=(x, 0))))
    cs = counter_scale if counter_scale is not None else (100, 100)
    icons.append(group([rect(11, 11, 3), stroke(DARK, 1.6)], tr(p=(0, 0), s=cs), nm="counter"))
    return group(icons + [group([rect(SW, 28, 0), fill(BAR)])], tr(p=(CX, y), o=o), nm="bottom")


def content_lines(x0=SL + 12, y0=ST + 40, n=9, gap=15, widths=None):
    widths = widths or [96, 70, 100, 84, 60, 98, 76, 90, 64, 88, 72, 100]
    items = [group([rect(SW - 24, 46, 8), fill(ACCENT_SOFT)], tr(p=(CX, y0 + 23)))]
    y = y0 + 58
    for i in range(n):
        w = widths[i % len(widths)]
        items.append(group([rect(w, 6, 3), fill(LINE if i % 3 else LINE2)], tr(p=(x0 + w / 2, y))))
        y += gap
    return items


def finger(keys_p, keys_o, keys_s=None, ripple_at=None):
    """指(丸)。ripple_at=(frame, x, y) で押した所に波紋を出す。"""
    shapes = [group([ellipse(22), fill(FINGER, 55), stroke(WHITE, 2.5)],
                    tr(s=an(keys_s) if keys_s else (100, 100)), nm="tip")]
    L = [layer("finger", shapes, p=an([(t, [x, y, 0]) for t, (x, y) in keys_p]), o=an(keys_o, STD))]
    if ripple_at:
        for (f, x, y) in ripple_at:
            L.append(layer("ripple", [group([ellipse(an([(f, [8, 8]), (f + 24, [46, 46])], STD)),
                                             stroke(ACCENT, 2.5)],
                                            tr(o=an([(f - 1, 0), (f, 90), (f + 24, 0)], STD)))],
                           p=(x, y)))
    return L


def write(name, layers):
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, name + ".json"), "w", encoding="utf-8") as f:
        json.dump(doc(name, layers), f, ensure_ascii=False, separators=(",", ":"))
    _ind[0] = 0


# ======================================================================
# 1. タブの切り替え: タブボタンを押す → タブの一覧が広がる → 1 つ選ぶと拡大
# ======================================================================
def tabs():
    L = []
    btn = (CX, SB - 14)
    L += finger([(0, (CX + 60, SB + 20)), (24, btn), (150, (CX + 29, CY + 42)), (214, (CX + 29, CY + 42))],
                [(0, 0), (16, 100), (190, 100), (205, 0)],
                [(30, [100, 100]), (38, [82, 82]), (48, [100, 100]), (150, [100, 100]), (158, [82, 82]), (168, [100, 100])],
                ripple_at=[(38, btn[0], btn[1]), (158, CX + 29, CY + 42)])
    # タブの一覧
    cards = []
    pos = [(-29, -38), (29, -38), (-29, 46), (29, 46)]
    colors = [ACCENT_SOFT, rgb("#C4EED0"), rgb("#FFDDB0"), rgb("#F9D5E5")]
    for i, (dx, dy) in enumerate(pos):
        a0 = 52 + i * 6
        if i == 3:
            # 選んだタブ: 画面いっぱいに広がる
            sc = an([(a0, [55, 55]), (a0 + 24, [100, 100]), (165, [100, 100]), (196, [238, 283])])
            p = an([(165, [dx, dy]), (196, [0, 0])])
        else:
            sc = an([(a0, [55, 55]), (a0 + 24, [100, 100])])
            p = st([dx, dy])
        o = an([(a0, 0), (a0 + 14, 100), (165, 100), (185, 0 if i != 3 else 100)], STD)
        cards.append(group([
            group([rect(46, 6, 3), fill(LINE)], tr(p=(0, -26))),
            group([rect(44, 46, 6), fill(colors[i])], tr(p=(0, 6))),
            group([rect(52, 72, 9), fill(WHITE)]),
        ], tr(p=p, s=sc, o=o), nm="card%d" % i))
    L.append(layer("grid", cards, p=(CX, CY - 4), o=an([(205, 100), (230, 0)], STD), mask=None))
    L.append(layer("gridbg", [group([rect(SW, SH, 15), fill(rgb("#EEF1FA"))])], p=(CX, CY),
                   o=an([(44, 0), (58, 100), (196, 100), (212, 0)], STD)))
    L.append(layer("ui", [address_bar(), bottom_bar(counter_scale=an([(36, [100, 100]), (42, [80, 80]), (54, [100, 100])]))] , p=(0, 0)))
    L.append(layer("page", content_lines(), p=(0, 0), mask=SCREEN_MASK))
    L.append(phone())
    write("tabs", L)


# ======================================================================
# 2. 検索・URL 入力: アドレスバーを押す → 文字を入力 → 候補が出る
# ======================================================================
def search():
    L = []
    bar = (CX, ST + 16)
    L += finger([(0, (CX + 50, CY + 60)), (26, bar), (60, bar)], [(0, 0), (14, 100), (52, 100), (64, 0)],
                [(30, [100, 100]), (36, [82, 82]), (46, [100, 100])], ripple_at=[(36, bar[0], bar[1])])
    # 入力した文字(少しずつ伸びる)
    typed = an([(70, [0, 6]), (82, [14, 6]), (94, [24, 6]), (106, [38, 6]), (118, [50, 6]), (130, [62, 6])], LINEAR)
    caret_x = an([(70, [-40, 0]), (82, [-26, 0]), (94, [-16, 0]), (106, [-2, 0]), (118, [10, 0]), (130, [22, 0])], LINEAR)
    blink = an([(t, 100 if (t // 15) % 2 == 0 else 0) for t in range(46, 230, 15)], HOLD)
    active_bar = group([
        group([rect(10, 10, 5), stroke(DARK, 2)], tr(p=(-46, -1))),
        group([path([(0, 0), (4, 4)]), stroke(DARK, 2)], tr(p=(-42, 3))),
        group([rect(typed, 0, 3, p=an([(70, [-34, 0]), (82, [-27, 0]), (94, [-22, 0]), (106, [-15, 0]), (118, [-9, 0]), (130, [-3, 0])], LINEAR)), fill(DARK)]),
        group([rect(2, 12, 1), fill(ACCENT)], tr(p=caret_x, o=blink)),
        group([rect(SW - 12, 22, 11), fill(WHITE), stroke(ACCENT, 2)]),
    ], tr(p=(CX, ST + 16), o=an([(38, 0), (48, 100), (212, 100), (228, 0)], STD)), nm="active")
    rows = []
    for i in range(4):
        f0 = 136 + i * 8
        rows.append(group([
            group([ellipse(10), fill(LINE)], tr(p=(-44, 0))),
            group([rect([60, 52, 70, 44][i], 6, 3), fill(LINE if i else ACCENT_SOFT)],
                  tr(p=(-34 + [60, 52, 70, 44][i] / 2, 0))),
            group([path([(0, 0), (6, -6)]), stroke(LINE, 2)], tr(p=(44, 3))),
        ], tr(p=an([(f0, [0, 30 + i * 24]), (f0 + 20, [0, 22 + i * 24])]),
              o=an([(f0, 0), (f0 + 14, 100), (212, 100), (228, 0)], STD))))
    L.append(layer("suggest", rows, p=(CX, ST + 16)))
    L.append(layer("active", [active_bar], p=(0, 0)))
    L.append(layer("sheet", [group([rect(SW, SH - 30, 0), fill(WHITE)], tr(p=(0, (SH - 30) / 2 + 15)))],
                   p=(CX, ST + 15), o=an([(40, 0), (54, 100), (212, 100), (228, 0)], STD), mask=SCREEN_MASK))
    L.append(layer("ui", [address_bar(), bottom_bar()], p=(0, 0)))
    L.append(layer("page", content_lines(), p=(0, 0), mask=SCREEN_MASK))
    L.append(phone())
    write("search", L)


# ======================================================================
# 3. 引っ張って更新: 指でページを下へ引く → くるくる → 戻る
# ======================================================================
def refresh():
    L = []
    top = (CX, ST + 50)
    L += finger([(0, (CX, ST + 50)), (40, (CX, ST + 50)), (100, (CX, ST + 120)), (130, (CX, ST + 120))],
                [(0, 0), (20, 100), (110, 100), (126, 0)])
    pull = an([(40, [0, 0]), (100, [0, 54]), (110, [0, 54]), (190, [0, 54]), (214, [0, 0])])
    spinner = group([
        group([ellipse(20), trim(an([(40, 5), (100, 80), (110, 75), (200, 75)], LINEAR), 0,
                                   an([(100, 0), (200, 720)], LINEAR)), stroke(ACCENT, 3)],
              tr(r=an([(100, 0), (200, 540)], LINEAR))),
        group([ellipse(34), fill(WHITE)]),
    ], tr(p=an([(40, [0, -10]), (100, [0, 34]), (190, [0, 34]), (214, [0, -10])]),
          s=an([(40, [40, 40]), (100, [100, 100]), (196, [100, 100]), (214, [30, 30])]),
          o=an([(40, 0), (60, 100), (196, 100), (214, 0)], STD)), nm="spinner")
    L.append(layer("spinner", [spinner], p=(CX, ST + 24), mask=SCREEN_MASK))
    page = group(content_lines(y0=ST + 40), tr(p=pull), nm="pagec")
    L.append(layer("page", [page], p=(0, 0), mask=(SL, ST + 28, SW, SH - 56)))
    L.append(layer("ui", [address_bar(), bottom_bar()], p=(0, 0)))
    L.append(phone())
    write("refresh", L)


# ======================================================================
# 4. スクロールでバーが隠れる: 上へスクロールするとバーが隠れ、戻すと出てくる
# ======================================================================
def scroll():
    L = []
    L += finger([(0, (CX + 20, SB - 40)), (20, (CX + 20, SB - 40)), (70, (CX + 20, ST + 70)),
                 (130, (CX + 20, ST + 70)), (180, (CX + 20, SB - 50))],
                [(0, 0), (14, 100), (74, 100), (90, 0), (124, 0), (134, 100), (182, 100), (196, 0)])
    hide_top = an([(20, [0, 0]), (70, [0, -34]), (130, [0, -34]), (180, [0, 0])])
    hide_bottom = an([(20, [0, 0]), (70, [0, 34]), (130, [0, 34]), (180, [0, 0])])
    content = group(content_lines(n=14), tr(p=an([(20, [0, 0]), (70, [0, -70]), (130, [0, -70]), (180, [0, -30])])))
    L.append(layer("top", [group([address_bar()], tr(p=hide_top))], p=(0, 0), mask=SCREEN_MASK))
    L.append(layer("bottom", [group([bottom_bar()], tr(p=hide_bottom))], p=(0, 0), mask=SCREEN_MASK))
    L.append(layer("page", [content], p=(0, 0), mask=SCREEN_MASK))
    L.append(phone())
    write("scroll", L)


# ======================================================================
# 5. 長押しメニュー: リンクを長押し → 波紋 → メニューが下から出る
# ======================================================================
def longpress():
    L = []
    link = (SL + 50, ST + 118)
    L += finger([(0, (CX + 40, SB - 30)), (24, link), (100, link)],
                [(0, 0), (14, 100), (92, 100), (106, 0)],
                [(28, [100, 100]), (36, [84, 84]), (92, [84, 84]), (104, [100, 100])])
    # 長押しの間、輪が広がっていく
    L.append(layer("hold", [group([ellipse(an([(34, [10, 10]), (84, [56, 56])], LINEAR)),
                                   trim(an([(34, 0), (84, 100)], LINEAR)), stroke(ACCENT, 3)],
                                  tr(r=-90, o=an([(33, 0), (34, 100), (84, 100), (96, 0)], STD)))],
                   p=link))
    rows = []
    for i in range(4):
        rows.append(group([
            group([ellipse(10), fill([ACCENT, GREEN, YELLOW, PINK][i])], tr(p=(-42, 0))),
            group([rect([58, 46, 66, 50][i], 6, 3), fill(LINE)], tr(p=(-30 + [58, 46, 66, 50][i] / 2, 0))),
        ], tr(p=(0, -44 + i * 22))))
    sheet = group(rows + [
        group([rect(24, 4, 2), fill(LINE)], tr(p=(0, -66))),
        group([rect(SW, 150, 18), fill(WHITE)], tr(p=(0, 0))),
    ], tr(p=an([(86, [0, 150]), (110, [0, 0]), (200, [0, 0]), (222, [0, 150])])))
    L.append(layer("sheet", [sheet], p=(CX, SB - 60), mask=SCREEN_MASK))
    L.append(layer("scrim", [group([rect(SW, SH, 15), fill(rgb("#000000"))])], p=(CX, CY),
                   o=an([(86, 0), (104, 30), (200, 30), (220, 0)], STD)))
    # リンク(青い下線付きの文字)
    L.append(layer("link", [group([rect(62, 6, 3), fill(ACCENT)]),
                            group([rect(62, 1.5, 1), fill(ACCENT)], tr(p=(0, 6)))],
                   p=(SL + 46, ST + 118), s=an([(34, [100, 100]), (40, [96, 96]), (86, [96, 96]), (96, [100, 100])])))
    L.append(layer("ui", [address_bar(), bottom_bar()], p=(0, 0)))
    L.append(layer("page", content_lines(widths=[96, 70, 100, 84, 4, 98, 76, 90, 64]), p=(0, 0), mask=SCREEN_MASK))
    L.append(phone())
    write("longpress", L)


# ======================================================================
# 6. 戻る: 画面の左端から右へスワイプ → 矢印が出る → 前のページへ
# ======================================================================
def back():
    L = []
    L += finger([(0, (SL - 4, CY)), (24, (SL + 4, CY)), (80, (SL + 56, CY)), (100, (SL + 56, CY))],
                [(0, 0), (16, 100), (84, 100), (98, 0)])
    arrow = group([
        group([path([(4, -7), (-3, 0), (4, 7)]), stroke(DARK, 2.6)]),
        group([path([(-3, 0), (8, 0)]), stroke(DARK, 2.6)]),
        group([ellipse(30), fill(WHITE)]),
    ], tr(p=an([(24, [-18, 0]), (80, [16, 0]), (86, [16, 0]), (100, [-18, 0])]),
          s=an([(24, [60, 60]), (70, [100, 100]), (80, [112, 112]), (90, [100, 100])]),
          o=an([(24, 0), (40, 100), (90, 100), (100, 0)], STD)))
    L.append(layer("arrow", [arrow], p=(SL, CY), mask=SCREEN_MASK))
    # 今のページ(右へずれて消える)と前のページ(左から出てくる)
    cur = group(content_lines(widths=[60, 96, 76, 100, 70, 84, 98, 64, 90]),
                tr(p=an([(86, [0, 0]), (120, [SW, 0]), (190, [SW, 0]), (191, [0, 0])]),
                   o=an([(86, 100), (120, 0), (200, 0), (220, 100)], STD)))
    prev = group([group([rect(SW - 24, 46, 8), fill(rgb("#C4EED0"))], tr(p=(CX, ST + 63)))]
                 + content_lines(y0=ST + 40, widths=[88, 64, 100, 72, 96, 60, 80, 92, 70])[1:],
                 tr(p=an([(86, [-40, 0]), (120, [0, 0])]), o=an([(86, 0), (112, 100), (200, 100), (220, 0)], STD)))
    L.append(layer("cur", [cur], p=(0, 0), mask=SCREEN_MASK))
    L.append(layer("prev", [prev], p=(0, 0), mask=SCREEN_MASK))
    L.append(layer("ui", [address_bar(), bottom_bar()], p=(0, 0)))
    L.append(phone())
    write("back", L)


if __name__ == "__main__":
    tabs()
    search()
    refresh()
    scroll()
    longpress()
    back()
    print("written to", os.path.abspath(OUT))
