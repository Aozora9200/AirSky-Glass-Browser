"""python-dotenv の互換(AirSky Browser)。

本物の python-dotenv をプロジェクトに入れている時はそちらが優先される(このフォルダは検索順の最後)。
AirSky は実行前にプログラムのフォルダの .env を環境変数に入れているので、load_dotenv() を呼ばなくても
os.environ から読める。
"""
import io
import os
import re
from collections import OrderedDict

__all__ = ["load_dotenv", "dotenv_values", "find_dotenv", "get_key", "set_key", "unset_key"]

_LINE = re.compile(r"""^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_.\-]*)\s*(?:=\s*(.*))?$""")
_VAR = re.compile(r"\$\{([A-Za-z_][A-Za-z0-9_]*)(?::-([^}]*))?\}")


def _parse(stream):
    out = OrderedDict()
    text = stream.read()
    lines = text.splitlines()
    i = 0
    while i < len(lines):
        line = lines[i]
        i += 1
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        m = _LINE.match(line)
        if not m:
            continue
        key, raw = m.group(1), m.group(2)
        if raw is None:
            out[key] = None
            continue
        raw = raw.strip()
        if raw[:1] in ("'", '"'):
            q = raw[0]
            body = raw[1:]
            while not _closed(body, q) and i < len(lines):
                body += "\n" + lines[i]
                i += 1
            end = _close_index(body, q)
            val = body[:end] if end >= 0 else body
            if q == '"':
                val = (val.replace("\\n", "\n").replace("\\t", "\t").replace('\\"', '"')
                       .replace("\\\\", "\\"))
            out[key] = (val, q == "'")
        else:
            val = re.split(r"\s+#", raw, 1)[0].strip()
            out[key] = (val, False)
    return out


def _closed(body, q):
    return _close_index(body, q) >= 0


def _close_index(body, q):
    k = 0
    while k < len(body):
        if body[k] == "\\" and q == '"':
            k += 2
            continue
        if body[k] == q:
            return k
        k += 1
    return -1


def _resolve(values, interpolate, override):
    out = OrderedDict()
    for key, v in values.items():
        if v is None:
            out[key] = None
            continue
        val, literal = v
        if interpolate and not literal:
            def rep(m):
                name, default = m.group(1), m.group(2)
                if override:
                    got = out.get(name) if out.get(name) is not None else os.environ.get(name)
                else:
                    got = os.environ.get(name) if name in os.environ else out.get(name)
                return got if got not in (None, "") else (default or "")
            val = _VAR.sub(rep, val)
        out[key] = val
    return out


def find_dotenv(filename=".env", raise_error_if_not_found=False, usecwd=False):
    start = os.getcwd()
    if not usecwd:
        import sys
        main = getattr(sys.modules.get("__main__"), "__file__", None)
        if main:
            start = os.path.dirname(os.path.abspath(main))
    path = start
    while True:
        cand = os.path.join(path, filename)
        if os.path.isfile(cand):
            return cand
        parent = os.path.dirname(path)
        if parent == path:
            break
        path = parent
    if raise_error_if_not_found:
        raise IOError("File not found")
    return ""


def _open(dotenv_path, stream, encoding):
    if stream is not None:
        return stream
    path = dotenv_path or find_dotenv(usecwd=False) or find_dotenv(usecwd=True)
    if not path or not os.path.isfile(path):
        return None
    return io.open(path, encoding=encoding or "utf-8")


def dotenv_values(dotenv_path=None, stream=None, verbose=False, interpolate=True, encoding="utf-8"):
    f = _open(dotenv_path, stream, encoding)
    if f is None:
        return OrderedDict()
    with f:
        return _resolve(_parse(f), interpolate, True)


def load_dotenv(dotenv_path=None, stream=None, verbose=False, override=False, interpolate=True,
                encoding="utf-8"):
    f = _open(dotenv_path, stream, encoding)
    if f is None:
        return False
    with f:
        values = _resolve(_parse(f), interpolate, override)
    for k, v in values.items():
        if v is None:
            continue
        if override or k not in os.environ:
            os.environ[k] = v
    return True


def get_key(dotenv_path, key_to_get, encoding="utf-8"):
    return dotenv_values(dotenv_path, encoding=encoding).get(key_to_get)


def _rewrite(dotenv_path, key, value, remove, quote_mode="always", export=False, encoding="utf-8"):
    lines = []
    if os.path.isfile(dotenv_path):
        with io.open(dotenv_path, encoding=encoding) as f:
            lines = f.read().splitlines()
    found = False
    out = []
    for line in lines:
        m = _LINE.match(line)
        if m and m.group(1) == key:
            found = True
            if remove:
                continue
            line = _fmt(key, value, quote_mode, export)
        out.append(line)
    if not found and not remove:
        out.append(_fmt(key, value, quote_mode, export))
    with io.open(dotenv_path, "w", encoding=encoding) as f:
        f.write("\n".join(out) + ("\n" if out else ""))
    return found


def _fmt(key, value, quote_mode, export):
    v = str(value)
    if quote_mode == "always" or (quote_mode == "auto" and not v.isalnum()):
        v = "'%s'" % v.replace("'", "\\'")
    return ("export " if export else "") + "%s=%s" % (key, v)


def set_key(dotenv_path, key_to_set, value_to_set, quote_mode="always", export=False, encoding="utf-8"):
    _rewrite(dotenv_path, key_to_set, value_to_set, False, quote_mode, export, encoding)
    return True, key_to_set, value_to_set


def unset_key(dotenv_path, key_to_unset, quote_mode="always", encoding="utf-8"):
    found = _rewrite(dotenv_path, key_to_unset, None, True, encoding=encoding)
    return (True if found else None), key_to_unset


class Dotenv:
    pass
