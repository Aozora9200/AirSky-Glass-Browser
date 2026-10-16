"""tkinter.ttk の互換(AirSky Browser)。見た目は Android の Pixel 風の部品になる。"""

import weakref

import _airsky_gui as _g
import tkinter
from tkinter import _px, _bool, _cnfmerge, _dispatch, Variable, StringVar, IntVar, DoubleVar, Misc

__all__ = ["Button", "Checkbutton", "Combobox", "Entry", "Frame", "Label", "Labelframe",
           "LabelFrame", "Menubutton", "Notebook", "Panedwindow", "PanedWindow", "Progressbar",
           "Radiobutton", "Scale", "Scrollbar", "Separator", "Sizegrip", "Spinbox", "Style",
           "Treeview", "LabeledScale", "OptionMenu", "tclobjs_to_py", "setup_master"]

_styles = {}
_ttk_widgets = weakref.WeakSet()


def tclobjs_to_py(adict):
    return adict


def setup_master(master=None):
    return master or tkinter._get_default_root()


class Style:
    _name = "ttk::style"

    def __init__(self, master=None):
        self.master = master

    def configure(self, style, query_opt=None, **kw):
        if query_opt is not None:
            return _styles.get(style, {}).get(query_opt)
        if not kw:
            return dict(_styles.get(style, {}))
        _styles.setdefault(style, {}).update(kw)
        for w in list(_ttk_widgets):
            if not w._destroyed:
                w._send_props()

    def map(self, style, query_opt=None, **kw):
        return {}

    def lookup(self, style, option, state=None, default=None):
        return _styles.get(style, {}).get(option.lstrip("-"), default)

    def layout(self, style, layoutspec=None):
        return []

    def element_create(self, elementname, etype, *args, **kw):
        pass

    def element_names(self):
        return ()

    def element_options(self, elementname):
        return ()

    def theme_create(self, themename, parent=None, settings=None):
        pass

    def theme_settings(self, themename, settings):
        pass

    def theme_names(self):
        return ("airsky", "clam", "alt", "default", "classic")

    def theme_use(self, themename=None):
        return "airsky"


class Widget(tkinter.Widget):
    _ttk_class = "TFrame"

    def _style_props(self, p):
        merged = {}
        for name in (".", self._ttk_class, str(self._opts.get("style") or "")):
            if name and name in _styles:
                merged.update(_styles[name])
        for k, v in merged.items():
            k = tkinter._norm_opt(k)
            if k not in self._opts or self._opts.get(k) in (None, ""):
                if k == "foreground":
                    c = _g.color(v)
                    if c:
                        p["fg"] = c
                elif k in ("background", "fieldbackground"):
                    c = _g.color(v)
                    if c and self._ttk_class not in ("TEntry", "TCombobox"):
                        p["bg"] = c
                elif k == "font":
                    f = _g.font(v)
                    if f:
                        p["font"] = f
                elif k == "padding":
                    pad = _pad4(v)
                    if pad:
                        p["padding"] = pad
        tkinter.Misc._style_props(self, p)
        pad = _pad4(self._opts.get("padding"))
        if pad:
            p["padding"] = pad
        return p

    def _setup(self, master, cnf, kw, name=None):
        tkinter.Misc._setup(self, master, cnf, kw, name)
        self._ttk_state = set()
        _ttk_widgets.add(self)

    def state(self, statespec=None):
        if statespec is None:
            return tuple(self._ttk_state)
        for s in statespec if isinstance(statespec, (list, tuple)) else str(statespec).split():
            if s.startswith("!"):
                self._ttk_state.discard(s[1:])
            else:
                self._ttk_state.add(s)
        disabled = "disabled" in self._ttk_state
        readonly = "readonly" in self._ttk_state
        self._opts["state"] = "disabled" if disabled else ("readonly" if readonly else "normal")
        self._send_props()

    def instate(self, statespec, callback=None, *args, **kw):
        ok = all((s[1:] not in self._ttk_state) if s.startswith("!") else (s in self._ttk_state)
                 for s in (statespec if isinstance(statespec, (list, tuple)) else str(statespec).split()))
        if ok and callback:
            return callback(*args, **kw)
        return ok

    def identify(self, x, y):
        return ""


def _pad4(v):
    if v in (None, ""):
        return None
    vals = v if isinstance(v, (tuple, list)) else str(v).split()
    vals = [_px(x) for x in vals]
    if len(vals) == 1:
        return [vals[0]] * 4
    if len(vals) == 2:
        return [vals[0], vals[1], vals[0], vals[1]]
    if len(vals) == 3:
        return [vals[0], vals[1], vals[2], vals[1]]
    return vals[:4]


class Frame(Widget, tkinter.Frame):
    _ttk_class = "TFrame"


class Label(Widget, tkinter.Label):
    _ttk_class = "TLabel"


class Button(Widget, tkinter.Button):
    _ttk_class = "TButton"

    def _props(self):
        p = tkinter.Button._props(self)
        style = str(self._opts.get("style") or "")
        if "accent" in style.lower() or "primary" in style.lower():
            p["style"] = "filled"
        return p


class Checkbutton(Widget, tkinter.Checkbutton):
    _ttk_class = "TCheckbutton"

    def _props(self):
        p = tkinter.Checkbutton._props(self)
        if "switch" in str(self._opts.get("style") or "").lower():
            p["switch"] = True
        return p


class Radiobutton(Widget, tkinter.Radiobutton):
    _ttk_class = "TRadiobutton"


class Entry(Widget, tkinter.Entry):
    _ttk_class = "TEntry"


class Labelframe(Widget, tkinter.LabelFrame):
    _ttk_class = "TLabelframe"


LabelFrame = Labelframe


class Menubutton(Widget, tkinter.Menubutton):
    _ttk_class = "TMenubutton"


class Scrollbar(Widget, tkinter.Scrollbar):
    _ttk_class = "TScrollbar"


class Scale(Widget, tkinter.Scale):
    _ttk_class = "TScale"
    _defaults = {"from": 0, "to": 1, "resolution": 0, "orient": "horizontal", "showvalue": 0}

    def get(self, x=None, y=None):
        return self._value


class Spinbox(Widget, tkinter.Spinbox):
    _ttk_class = "TSpinbox"


class Separator(Widget):
    _type = "separator"
    _ttk_class = "TSeparator"
    _wclass = "TSeparator"

    def _props(self):
        return {"orient": str(self._opts.get("orient", "horizontal"))}


class Sizegrip(Widget):
    _type = "space"
    _ttk_class = "TSizegrip"

    def _props(self):
        return {"width": 0, "height": 0}


class Panedwindow(Widget, tkinter.PanedWindow):
    _ttk_class = "TPanedwindow"
    _defaults = {"orient": "vertical"}

    def insert(self, pos, child, **kw):
        self.add(child, **kw)


PanedWindow = Panedwindow


class Progressbar(Widget):
    _type = "progress"
    _ttk_class = "Horizontal.TProgressbar"
    _wclass = "TProgressbar"
    _defaults = {"maximum": 100, "mode": "determinate", "orient": "horizontal", "value": 0}

    def _init(self):
        self._timer = None

    def _cur(self):
        v = self._opts.get("variable")
        if isinstance(v, Variable):
            try:
                return float(v._value)
            except (TypeError, ValueError):
                return 0.0
        return float(self._opts.get("value", 0) or 0)

    def _props(self):
        p = self._style_props({})
        p["value"] = self._cur()
        p["max"] = float(self._opts.get("maximum", 100) or 100)
        p["mode"] = str(self._opts.get("mode", "determinate"))
        p["running"] = self._timer is not None
        ln = self._opts.get("length")
        if ln:
            p["width"] = _px(ln)
        return p

    def __setitem__(self, key, value):
        self.configure({key: value})

    def step(self, amount=None):
        amount = 1.0 if amount is None else float(amount)
        mx = float(self._opts.get("maximum", 100) or 100)
        v = (self._cur() + amount) % mx if self._cur() + amount >= mx else self._cur() + amount
        var = self._opts.get("variable")
        if isinstance(var, Variable):
            var.set(v)
        else:
            self._opts["value"] = v
            self._send_props()

    def start(self, interval=None):
        if self._timer is None:
            self._timer = True
            self._send_props()
            if str(self._opts.get("mode")) != "indeterminate":
                self._tick(int(interval or 50))

    def _tick(self, interval):
        if self._timer is None or self._destroyed:
            return
        self.step()
        self._timer = self.after(interval, self._tick, interval) or True

    def stop(self):
        if self._timer not in (None, True):
            self.after_cancel(self._timer)
        self._timer = None
        self._send_props()


class Combobox(Widget, tkinter.Entry):
    _type = "combo"
    _ttk_class = "TCombobox"
    _wclass = "TCombobox"

    def _values(self):
        v = self._opts.get("values") or ()
        if isinstance(v, str):
            v = tkinter.Listbox._split(v)
        return [str(x) for x in v]

    def _props(self):
        p = tkinter.Entry._props(self)
        p["items"] = self._values()
        p["readonly"] = str(self._opts.get("state", "normal")) == "readonly"
        if p["readonly"]:
            p.pop("state", None)
        return p

    def current(self, newindex=None):
        vals = self._values()
        if newindex is None:
            try:
                return vals.index(self._text)
            except ValueError:
                return -1
        self.set(vals[int(newindex)])

    def set(self, value):
        self._set_text(str(value))

    def _on_value(self, value, ev):
        tkinter.Entry._on_value(self, value, ev)
        if ev.get("selected"):
            _dispatch(self, "Virtual", "ComboboxSelected", _g.Event(widget=self, type="Virtual"))
            cmd = self._opts.get("postcommand")


class Notebook(Widget):
    _type = "notebook"
    _ttk_class = "TNotebook"
    _wclass = "TNotebook"
    _container = True

    def _init(self):
        self._tabs = []  # [child, {"text":...}]
        self._selected = 0

    def _props(self):
        p = self._style_props({})
        p["tabs"] = [str(o.get("text", "")) for c, o in self._tabs if o.get("state") != "hidden"]
        p["selected"] = self._selected
        for k in ("width", "height"):
            if self._opts.get(k):
                p[k] = _px(self._opts[k])
        return p

    def _sync(self):
        b = _g.bridge()
        for c, o in self._tabs:
            b.send({"op": "layout", "id": c._id, "opts": {"m": "tab"}})
        b.send({"op": "order", "id": self._id, "ids": [c._id for c, o in self._tabs]})
        self._send_props()

    def _find(self, tab_id):
        if isinstance(tab_id, int):
            return tab_id
        if tab_id in ("current",):
            return self._selected
        if tab_id in ("end",):
            return len(self._tabs)
        if isinstance(tab_id, str) and tab_id.isdigit():
            return int(tab_id)
        for i, (c, o) in enumerate(self._tabs):
            if c is tab_id or str(c) == str(tab_id):
                return i
        raise tkinter.TclError("can't find tab %s" % tab_id)

    def add(self, child, **kw):
        for i, (c, o) in enumerate(self._tabs):
            if c is child:
                o.update(kw)
                o.pop("state", None) if kw.get("state") != "hidden" else None
                self._sync()
                return
        self._tabs.append([child, dict(kw)])
        self._sync()

    def insert(self, pos, child, **kw):
        i = self._find(pos)
        self._tabs.insert(i, [child, dict(kw)])
        self._sync()

    def forget(self, tab_id):
        i = self._find(tab_id)
        c, o = self._tabs.pop(i)
        _g.bridge().send({"op": "layout", "id": c._id, "opts": {"m": "none"}})
        self._selected = max(0, min(self._selected, len(self._tabs) - 1))
        self._sync()

    def hide(self, tab_id):
        self._tabs[self._find(tab_id)][1]["state"] = "hidden"
        self._sync()

    def select(self, tab_id=None):
        if tab_id is None:
            return str(self._tabs[self._selected][0]) if self._tabs else ""
        self._selected = self._find(tab_id)
        self._send_props()
        _dispatch(self, "Virtual", "NotebookTabChanged", _g.Event(widget=self, type="Virtual"))

    def index(self, tab_id):
        return self._find(tab_id)

    def tab(self, tab_id, option=None, **kw):
        o = self._tabs[self._find(tab_id)][1]
        if option:
            return o.get(option)
        if not kw:
            return dict(o)
        o.update(kw)
        self._sync()

    def tabs(self):
        return tuple(str(c) for c, o in self._tabs)

    def enable_traversal(self):
        pass

    def _on_other(self, ev):
        if ev.get("ev") == "tab":
            self._selected = int(ev.get("index", 0))
            _dispatch(self, "Virtual", "NotebookTabChanged", _g.Event(widget=self, type="Virtual"))


class Treeview(Widget):
    """表(Treeview)。行の階層は字下げで表す。"""
    _type = "table"
    _ttk_class = "Treeview"
    _wclass = "Treeview"

    def _init(self):
        self._items = {"": {"children": [], "text": "", "values": [], "open": True, "tags": ()}}
        self._parent_of = {}
        self._headings = {"#0": {"text": ""}}
        self._columns = {}
        self._sel = []
        self._focus = ""
        self._iid = 0
        self._tag_opts = {}
        self._tag_binds = {}

    def _colnames(self):
        cols = self._opts.get("columns") or ()
        if isinstance(cols, str):
            cols = cols.split()
        return [str(c) for c in cols]

    def _shown(self):
        show = self._opts.get("show", "tree headings")
        show = show if isinstance(show, (list, tuple)) else str(show).split()
        return show

    def _visible_rows(self):
        out = []

        def walk(iid, depth):
            for c in self._items[iid]["children"]:
                out.append((c, depth))
                if self._items[c]["open"]:
                    walk(c, depth + 1)
        walk("", 0)
        return out

    def _props(self):
        p = self._style_props({})
        cols = self._colnames()
        disp = self._opts.get("displaycolumns", "#all")
        if disp not in ("#all", None, ""):
            dcols = [cols[int(c)] if str(c).isdigit() else str(c)
                     for c in (disp if isinstance(disp, (list, tuple)) else str(disp).split())]
        else:
            dcols = cols
        show = self._shown()
        tree = "tree" in show
        heads = []
        if tree:
            heads.append(self._headings.get("#0", {}).get("text", ""))
        heads += [self._headings.get(c, {}).get("text", c) for c in dcols]
        rows, ids = [], []
        for iid, depth in self._visible_rows():
            it = self._items[iid]
            vals = list(it["values"])
            cells = []
            if tree:
                arrow = ("▾ " if it["open"] else "▸ ") if it["children"] else ""
                cells.append("    " * depth + arrow + str(it["text"]))
            for c in dcols:
                k = cols.index(c) if c in cols else -1
                cells.append(str(vals[k]) if 0 <= k < len(vals) else "")
            rows.append(cells)
            ids.append(iid)
        self._row_ids = ids
        p["headings"] = heads if "headings" in show else []
        p["rows"] = rows
        p["selection"] = [ids.index(i) for i in self._sel if i in ids]
        p["hlines"] = int(_px(self._opts.get("height"), 10)) or 10
        widths = []
        if tree:
            widths.append(_px(self._columns.get("#0", {}).get("width", 120)))
        widths += [_px(self._columns.get(c, {}).get("width", 100)) for c in dcols]
        p["widths"] = widths
        p["select_mode"] = str(self._opts.get("selectmode", "extended"))
        return p

    def _changed(self):
        self._send_props()

    def insert(self, parent, index, iid=None, **kw):
        if iid is None:
            self._iid += 1
            iid = "I%03X" % self._iid
        iid = str(iid)
        if iid in self._items:
            raise tkinter.TclError('Item %s already exists' % iid)
        vals = kw.get("values", ())
        if isinstance(vals, str):
            vals = vals.split()
        self._items[iid] = {"children": [], "text": kw.get("text", ""), "values": list(vals),
                            "open": _bool(kw.get("open", False)), "tags": kw.get("tags", ()),
                            "image": kw.get("image")}
        parent = str(parent)
        kids = self._items[parent]["children"]
        if index in ("end", tkinter.END):
            kids.append(iid)
        else:
            kids.insert(int(index), iid)
        self._parent_of[iid] = parent
        self._changed()
        return iid

    def delete(self, *items):
        for iid in _flat(items):
            iid = str(iid)
            if iid not in self._items:
                continue
            for c in list(self._items[iid]["children"]):
                self.delete(c)
            par = self._parent_of.pop(iid, "")
            if iid in self._items[par]["children"]:
                self._items[par]["children"].remove(iid)
            self._items.pop(iid, None)
            if iid in self._sel:
                self._sel.remove(iid)
        self._changed()

    def detach(self, *items):
        self.delete(*items)

    def move(self, item, parent, index):
        item, parent = str(item), str(parent)
        old = self._parent_of.get(item, "")
        if item in self._items[old]["children"]:
            self._items[old]["children"].remove(item)
        kids = self._items[parent]["children"]
        if index in ("end", tkinter.END):
            kids.append(item)
        else:
            kids.insert(int(index), item)
        self._parent_of[item] = parent
        self._changed()

    reattach = move

    def get_children(self, item=None):
        return tuple(self._items[str(item or "")]["children"])

    def set_children(self, item, *newchildren):
        self._items[str(item)]["children"] = [str(c) for c in _flat(newchildren)]
        self._changed()

    def exists(self, item):
        return str(item) in self._items

    def parent(self, item):
        return self._parent_of.get(str(item), "")

    def index(self, item):
        par = self.parent(item)
        return self._items[par]["children"].index(str(item))

    def next(self, item):
        par = self.parent(item)
        kids = self._items[par]["children"]
        i = kids.index(str(item))
        return kids[i + 1] if i + 1 < len(kids) else ""

    def prev(self, item):
        par = self.parent(item)
        kids = self._items[par]["children"]
        i = kids.index(str(item))
        return kids[i - 1] if i > 0 else ""

    def item(self, item, option=None, **kw):
        it = self._items[str(item)]
        if option:
            return it.get(option, "")
        if not kw:
            return {"text": it["text"], "values": list(it["values"]), "open": it["open"],
                    "tags": list(it["tags"]) if it["tags"] else "", "image": it.get("image") or ""}
        for k, v in kw.items():
            if k == "values" and isinstance(v, str):
                v = v.split()
            if k == "open":
                v = _bool(v)
            it[k] = list(v) if k == "values" else v
        self._changed()

    def set(self, item, column=None, value=None):
        it = self._items[str(item)]
        cols = self._colnames()
        if column is None:
            return {c: (it["values"][i] if i < len(it["values"]) else "") for i, c in enumerate(cols)}
        k = int(str(column).lstrip("#")) - 1 if str(column).startswith("#") else cols.index(str(column))
        if value is None:
            return it["values"][k] if k < len(it["values"]) else ""
        while len(it["values"]) <= k:
            it["values"].append("")
        it["values"][k] = value
        self._changed()

    def heading(self, column, option=None, **kw):
        h = self._headings.setdefault(str(column), {"text": str(column) if column != "#0" else ""})
        if option:
            return h.get(option, "")
        if not kw:
            return dict(h)
        h.update(kw)
        self._changed()

    def column(self, column, option=None, **kw):
        c = self._columns.setdefault(str(column), {"width": 100})
        if option:
            return c.get(option, "")
        if not kw:
            return dict(c)
        c.update(kw)
        self._changed()

    def selection(self, selop=None, items=None):
        if selop is None:
            return tuple(self._sel)
        return ()

    def selection_set(self, *items):
        self._sel = [str(i) for i in _flat(items)]
        self._changed()

    def selection_add(self, *items):
        for i in _flat(items):
            if str(i) not in self._sel:
                self._sel.append(str(i))
        self._changed()

    def selection_remove(self, *items):
        for i in _flat(items):
            if str(i) in self._sel:
                self._sel.remove(str(i))
        self._changed()

    def selection_toggle(self, *items):
        for i in _flat(items):
            if str(i) in self._sel:
                self._sel.remove(str(i))
            else:
                self._sel.append(str(i))
        self._changed()

    def focus(self, item=None):
        if item is None:
            return self._focus
        self._focus = str(item)

    def see(self, item):
        pass

    def identify_row(self, y):
        return self._focus

    def identify_column(self, x):
        return "#1"

    def identify_region(self, x, y):
        return "cell"

    def identify(self, component, x, y):
        return self._focus

    def bbox(self, item, column=None):
        return ""

    def tag_configure(self, tagname, option=None, **kw):
        self._tag_opts.setdefault(tagname, {}).update(kw)

    def tag_bind(self, tagname, sequence=None, callback=None):
        if callback:
            tkinter._add_binding(self._tag_binds.setdefault(tagname, {}), sequence, callback, None)

    def tag_has(self, tagname, item=None):
        if item is None:
            return tuple(i for i, it in self._items.items() if tagname in (it["tags"] or ()))
        return tagname in (self._items[str(item)]["tags"] or ())

    def _on_value(self, value, ev):
        rows = getattr(self, "_row_ids", [])
        idx = [int(i) for i in (value or []) if 0 <= int(i) < len(rows)]
        self._sel = [rows[i] for i in idx]
        if self._sel:
            self._focus = self._sel[-1]
        if ev.get("toggle") and self._focus and self._items[self._focus]["children"]:
            it = self._items[self._focus]
            it["open"] = not it["open"]
            _dispatch(self, "Virtual", "TreeviewOpen" if it["open"] else "TreeviewClose",
                      _g.Event(widget=self, type="Virtual"))
            self._changed()
        _dispatch(self, "Virtual", "TreeviewSelect", _g.Event(widget=self, type="Virtual"))
        if ev.get("double"):
            _dispatch(self, "ButtonPress", "1", _g.Event(widget=self, type="ButtonPress", num=1),
                      frozenset({"Double"}))

    def xview(self, *args):
        return (0.0, 1.0)

    yview = xview


def _flat(items):
    out = []
    for i in items:
        if isinstance(i, (list, tuple)):
            out.extend(i)
        else:
            out.append(i)
    return out


class LabeledScale(Frame):
    def __init__(self, master=None, variable=None, from_=0, to=10, **kw):
        Frame.__init__(self, master, **kw)
        self._variable = variable or IntVar(value=from_)
        self.label = Label(self, textvariable=self._variable)
        self.label.pack(side="top")
        self.scale = Scale(self, variable=self._variable, from_=from_, to=to)
        self.scale.pack(side="top", fill="x")

    @property
    def value(self):
        return self._variable.get()

    @value.setter
    def value(self, v):
        self._variable.set(v)


class OptionMenu(tkinter.OptionMenu):
    def __init__(self, master, variable, default=None, *values, **kwargs):
        vals = list(values) or ([default] if default is not None else [""])
        if default is not None and isinstance(variable, Variable):
            variable.set(default)
        tkinter.OptionMenu.__init__(self, master, variable, *vals, **kwargs)

    def set_menu(self, default=None, *values):
        self._values = list(values)
        if default is not None:
            self._variable.set(default)
        self._send_props()
