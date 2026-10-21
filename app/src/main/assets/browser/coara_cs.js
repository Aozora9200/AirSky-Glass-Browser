// Coara Browser 拡張機能(プラグイン)をページで動かすコンテンツスクリプト。
//
// プラグインの本体と保存データは、アプリ(CoaraPlugins.java)→ background.js が
// contentScripts.register で登録した小さなスクリプトから届く:
//   __coaraBoot(プラグイン)          … プラグイン 1 つ分(ページ側で動かすコードなど)
//   __coaraStores[id] = 保存データ   … プラグインの保存データ(書き換わるたびに登録し直す)
// 登録したスクリプトとこのファイルのどちらが先に動くかは決まっていないので、先に届いた分は貯めておく。
//
// プラグインはページ側(メインワールド)で動かす(XMLHttpRequest や fetch を書き換えるものがあるため)。
// ページの CSP でインラインスクリプトが止められた時は、このコンテンツスクリプトの中で動かす。
// ページ側とは同期の CustomEvent でやり取りし(Coara の同期ブリッジの代わり)、
// アプリへはネイティブのポート("aozora_plg")で送る。
"use strict";

var __coaraPending = __coaraPending || [];
var __coaraStores = __coaraStores || {};

(function () {
  if (typeof __coaraBoot === "function") return;

  const CH = Math.random().toString(36).slice(2) + Date.now().toString(36);
  const REQ = "coara-req-" + CH;
  const RES = "coara-res-" + CH;
  const EVT = "coara-evt-" + CH;
  let isTop = false;
  try { isTop = window.top === window; } catch (e) {}
  let href = "";
  try { href = String(location.href); } catch (e) {}

  const plugins = {};      // id → プラグイン
  const waiting = {};      // 保存データ待ちのプラグイン id → true
  const started = {};      // ページ側で動き始めたエントリー(key → true)
  let listening = false;
  let port = null;

  function connect() {
    if (port) return port;
    try {
      port = browser.runtime.connectNative("aozora_plg");
      port.onMessage.addListener(onNative);
      port.onDisconnect.addListener(() => { port = null; });
      port.postMessage({ type: "hello", top: isTop, url: href });
    } catch (e) {
      port = null;
    }
    return port;
  }

  function post(msg) {
    const p = connect();
    if (!p) return false;
    try { p.postMessage(msg); return true; } catch (e) { return false; }
  }

  function emit(obj) {
    try {
      window.dispatchEvent(new CustomEvent(EVT, { detail: JSON.stringify(obj) }));
    } catch (e) {}
  }

  // アプリからの知らせ(GM_xmlhttpRequest の結果・メニューのコマンド)
  function onNative(m) {
    if (!m || typeof m !== "object") return;
    if (m.type === "xhr") {
      emit({ t: "xhr", k: m.key, rid: m.rid, res: m.res });
    } else if (m.type === "cmd") {
      const cid = String(m.cid || "");
      emit({ t: "cmd", k: cid.split(":")[0], cid: cid });
    } else if (m.type === "store") {
      // 別のタブで書き換わった保存データ
      if (m.id && m.store && typeof m.store === "object") __coaraStores[m.id] = { v: m.v || 0, data: m.store };
    }
  }

  function rx(list, u) {
    for (const s of list || []) {
      try { if (new RegExp(s).test(u)) return true; } catch (e) {}
    }
    return false;
  }

  function storeOf(id) {
    const s = __coaraStores[id];
    if (!s) {
      __coaraStores[id] = { v: 0, data: {} };
      return __coaraStores[id].data;
    }
    return s.data;
  }

  function has(p, perm) {
    return (p.perms || []).indexOf(perm) >= 0;
  }

  // ---- Coara の chrome.i18n.getMessage と同じ置き換え ----
  function i18n(p, key, subsJson) {
    const msgs = p.msgs || {};
    let subs = [];
    if (subsJson) {
      try { subs = JSON.parse(subsJson) || []; } catch (e) {}
    }
    if (key === "@@extension_id") return p.id;
    if (key === "@@ui_locale") return (p.uilang || "en").replace("-", "_");
    const entry = msgs[String(key).toLowerCase()];
    if (!entry || typeof entry.message !== "string") return "";
    const ph = entry.placeholders || {};
    let text = entry.message.replace(/\$([A-Za-z0-9_@]+)\$/g, (all, name) => {
      const def = ph[name] || ph[name.toLowerCase()];
      return def && typeof def.content === "string" ? def.content : all;
    });
    text = text.replace(/\$(\d+)/g, (all, n) => {
      const i = parseInt(n, 10) - 1;
      return i >= 0 && i < subs.length ? String(subs[i]) : "";
    });
    return text.replace(/\$\$/g, "$");
  }

  const MAX_KEY = 128;
  const MAX_VALUE = 65536;
  const MAX_KEYS = 256;

  // ページ側からの同期の呼び出し([id, key, op, a, b, c])。戻り値は文字列か null
  function handle(id, key, op, a, b, c) {
    const p = plugins[id];
    if (!p) return null;
    switch (op) {
      case "boot":
        started[key] = true;
        return "1";
      case "sget": {
        const s = storeOf(id);
        return Object.prototype.hasOwnProperty.call(s, a) ? String(s[a]) : null;
      }
      case "sset": {
        if (!a || a.length > MAX_KEY || b.length > MAX_VALUE) return "0";
        const s = storeOf(id);
        if (!Object.prototype.hasOwnProperty.call(s, a) && Object.keys(s).length >= MAX_KEYS) return "0";
        s[a] = b;
        post({ type: "op", id, op, a, b });
        return "1";
      }
      case "sdel": {
        const s = storeOf(id);
        delete s[a];
        post({ type: "op", id, op, a });
        return "1";
      }
      case "skeys":
        return JSON.stringify(Object.keys(storeOf(id)));
      case "sclear": {
        const s = __coaraStores[id];
        if (s) s.data = {};
        post({ type: "op", id, op });
        return "1";
      }
      case "res": {
        const r = p.res || {};
        return Object.prototype.hasOwnProperty.call(r, a) ? r[a] : null;
      }
      case "resurl": {
        const r = p.resUrl || {};
        return Object.prototype.hasOwnProperty.call(r, a) ? r[a] : null;
      }
      case "i18n":
        return i18n(p, a, b);
      case "uilang":
        return p.uilang || "";
      case "cmd":
        if (!isTop || !a || a.length > 64) return "0";
        return post({ type: "op", id, op, a, b }) ? "1" : "0";
      case "uncmd":
        post({ type: "op", id, op, a });
        return "1";
      case "xhr":
        if (!has(p, "xhr")) return "0";
        return post({ type: "xhr", id, key, rid: a, opts: b }) ? "1" : "0";
      case "xhrAbort":
        post({ type: "op", id, op, a });
        return "1";
      case "open":
        if (!has(p, "opentab") || !/^https?:\/\//i.test(a)) return "0";
        return post({ type: "op", id, op, a }) ? "1" : "0";
      case "notify":
        if (!has(p, "notification")) return "0";
        return post({ type: "op", id, op, a, b }) ? "1" : "0";
      case "clip":
        if (!has(p, "clipboard") || a.length > 200000) return "0";
        return post({ type: "op", id, op, a }) ? "1" : "0";
      case "log":
      case "ok":
      case "report":
        post({ type: "op", id, op, a, b, c });
        return "1";
      default:
        return null;
    }
  }

  function listen() {
    if (listening) return;
    listening = true;
    window.addEventListener(REQ, (e) => {
      let r = null;
      try {
        const args = JSON.parse(e.detail);
        r = handle(args[0], args[1], args[2], args[3], args[4], args[5]);
      } catch (x) {}
      try {
        window.dispatchEvent(new CustomEvent(RES, { detail: r }));
      } catch (x) {}
    }, true);
  }

  // ページ側(メインワールド)で動かす。CSP などで動かなかったら false
  function injectMain(code) {
    const root = document.documentElement;
    if (!root || !(document instanceof HTMLDocument)) return false;
    try {
      const s = document.createElement("script");
      s.textContent = code;
      root.appendChild(s);
      s.remove();
      return true;
    } catch (e) {
      return false;
    }
  }

  function runEntry(p, entry, index) {
    const code = entry.code + "(" + JSON.stringify(CH) + ");\n//# sourceURL=coara-plugin/" + p.id + "/" + index + ".js";
    injectMain(code);
    if (started[entry.key]) return;
    // ページ側で動かなかった(CSP でインラインスクリプトが止められた等): ここで動かす
    try {
      // eslint-disable-next-line no-new-func
      (new Function(code)).call(window);
    } catch (e) {
      post({ type: "op", id: p.id, op: "report", a: "load", b: "プラグインを実行できませんでした: " + (e && e.message ? e.message : String(e)), c: JSON.stringify({ url: href, stack: String(e && e.stack || "") }) });
    }
  }

  function start(p) {
    let any = false;
    (p.entries || []).forEach((entry, i) => {
      if (!entry.allFrames && !isTop) return;
      if (entry.excludes && entry.excludes.length && rx(entry.excludes, href)) return;
      if (!rx(entry.matches, href)) return;
      if (!any) {
        any = true;
        listen();
      }
      runEntry(p, entry, i);
    });
  }

  function boot(p) {
    if (!p || !p.id || plugins[p.id]) return;
    plugins[p.id] = p;
    if (__coaraStores[p.id]) {
      start(p);
    } else {
      // 保存データのスクリプトがまだ動いていない(この後すぐ動くはず)
      waiting[p.id] = true;
      setTimeout(() => {
        if (waiting[p.id]) {
          delete waiting[p.id];
          start(p);
        }
      }, 0);
    }
  }

  // 保存データが届いた(登録したスクリプトから呼ばれる)
  function onStore(id) {
    if (waiting[id] && plugins[id]) {
      delete waiting[id];
      start(plugins[id]);
    }
  }

  // eslint-disable-next-line no-global-assign
  __coaraBoot = boot;
  __coaraOnStore = onStore;
  const pending = __coaraPending.splice(0);
  for (const p of pending) boot(p);
})();

var __coaraBoot;
var __coaraOnStore;
