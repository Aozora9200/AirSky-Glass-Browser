// リーディングモード(各ページに注入されるコンテンツスクリプト)
// Readability.js(Firefox のリーダービューと同じ抽出エンジン)で本文を取り出し、
// ページの上に読みやすい表示を重ねる。ページの URL や履歴はそのままで、閉じれば元の表示に戻る。
// 本文はページ本体のスクロールで読む(ツールバーの自動非表示などがいつも通り効くように)。
// bridge.js から AozoraReader.toggle() / close() で呼ばれる。
/* global Readability */
var AozoraReader = (function () {
  "use strict";
  if (window.top !== window || typeof Readability !== "function") return null;

  const ROOT_CLASS = "aozora-reader-on";
  const STYLE_ID = "aozora-reader-style";
  const DEFAULTS = { size: 19, theme: "auto", font: "sans" };
  const THEMES = ["auto", "light", "sepia", "dark"];
  const THEME_LABELS = { auto: "自動", light: "白", sepia: "セピア", dark: "黒" };
  // アプリが英語表示の時の文字(bridge.js がアプリから受け取った言語を __airskyLang に入れる)
  const EN = {
    "自動": "Auto", "白": "Light", "セピア": "Sepia", "黒": "Dark", "明朝": "Serif", "ゴシック": "Sans",
    "リーディングモードを終了": "Exit reading mode", "リーディングモード": "Reading mode",
    "文字を小さく": "Smaller text", "文字を大きく": "Larger text", "書体を切り替え": "Change font",
    "背景色を切り替え": "Change background", "約 ": "", " 分で読めます": " min read",
  };
  function L(s) {
    return window.__airskyLang === "en" && Object.prototype.hasOwnProperty.call(EN, s) ? EN[s] : s;
  }

  let prefs = Object.assign({}, DEFAULTS);
  let host = null;
  let root = null;
  let savedScrollY = 0;
  let onChange = null;

  try {
    browser.storage.local.get("reader").then((r) => {
      if (r && r.reader) prefs = Object.assign({}, DEFAULTS, r.reader);
    }, () => {});
  } catch (e) {
    // storage が使えなくても既定の見た目で読める
  }

  function savePrefs() {
    try {
      browser.storage.local.set({ reader: prefs }).catch(() => {});
    } catch (e) {
      // 保存できなくても表示は変える
    }
  }

  function parse() {
    const clone = document.cloneNode(true);
    return new Readability(clone, { charThreshold: 200, keepClasses: false }).parse();
  }

  // 本文の HTML を、スクリプトやイベント属性を除いたノードにする(DOMParser の中では何も実行されない)
  function sanitize(html) {
    const doc = new DOMParser().parseFromString(html || "", "text/html");
    for (const el of doc.querySelectorAll("script, style, link, meta, iframe, object, embed, form, input, button, textarea, select")) {
      el.remove();
    }
    for (const el of doc.body.querySelectorAll("*")) {
      for (const attr of Array.from(el.attributes)) {
        const name = attr.name.toLowerCase();
        if (name.startsWith("on") || name === "style") {
          el.removeAttribute(attr.name);
        } else if ((name === "href" || name === "src" || name === "xlink:href")
            && /^\s*javascript:/i.test(attr.value)) {
          el.removeAttribute(attr.name);
        }
      }
      if (el.tagName === "A") {
        el.setAttribute("target", "_top");
        el.setAttribute("rel", "noopener");
      }
      if (el.tagName === "IMG") el.setAttribute("loading", "lazy");
    }
    return doc.body;
  }

  // 日本語(かな・漢字)は 1 分 500 字、それ以外は 1 分 230 語として読む時間を見積もる
  function readingMinutes(text) {
    const cjk = (text.match(/[぀-ヿ㐀-鿿]/g) || []).length;
    const words = text.replace(/[぀-ヿ㐀-鿿]/g, " ").split(/\s+/).filter(Boolean).length;
    return Math.max(1, Math.round(cjk / 500 + words / 230));
  }

  const PAGE_CSS =
    "html." + ROOT_CLASS + ", html." + ROOT_CLASS + " > body {" +
    " overflow: visible !important; height: auto !important; min-height: 0 !important;" +
    " margin: 0 !important; padding: 0 !important; display: block !important;" +
    " transform: none !important; filter: none !important; background: var(--aozora-reader-bg, #fff) !important; }" +
    "html." + ROOT_CLASS + " > body > *:not(aozora-reader) { display: none !important; }" +
    "html." + ROOT_CLASS + " > *:not(head):not(body) { display: none !important; }";

  const READER_CSS = `
    :host { all: initial; display: block !important; position: relative; z-index: 2147483647; }
    .wrap {
      --bg: #ffffff; --fg: #1b1b1f; --dim: #6b6b76; --line: rgba(0,0,0,.1); --link: #0b57d0; --bar: rgba(255,255,255,.86);
      background: var(--bg); color: var(--fg); min-height: 100vh;
      -webkit-text-size-adjust: 100%; text-size-adjust: 100%;
      animation: in .28s cubic-bezier(.2,.9,.3,1.05) both;
    }
    .wrap.closing { animation: out .2s ease-in both; }
    @keyframes in { from { opacity: 0; transform: translateY(14px); } to { opacity: 1; transform: none; } }
    @keyframes out { to { opacity: 0; transform: translateY(10px); } }
    .wrap.sepia { --bg: #f4ecd8; --fg: #5b4636; --dim: #8a7560; --line: rgba(91,70,54,.18); --link: #8a4b10; --bar: rgba(244,236,216,.88); }
    .wrap.dark { --bg: #1c1b22; --fg: #e6e4ea; --dim: #a19fa8; --line: rgba(255,255,255,.12); --link: #8ab4f8; --bar: rgba(28,27,34,.86); }
    @media (prefers-color-scheme: dark) {
      .wrap.auto { --bg: #1c1b22; --fg: #e6e4ea; --dim: #a19fa8; --line: rgba(255,255,255,.12); --link: #8ab4f8; --bar: rgba(28,27,34,.86); }
    }
    .bar {
      position: sticky; top: 0; z-index: 1; display: flex; align-items: center; gap: 6px;
      padding: 8px 12px; background: var(--bar); border-bottom: 1px solid var(--line);
      backdrop-filter: blur(12px); font: 14px/1.2 system-ui, "Noto Sans JP", sans-serif;
    }
    .bar .label { flex: 1; color: var(--dim); font-size: 13px; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
    .bar button {
      all: unset; box-sizing: border-box; min-width: 40px; height: 36px; padding: 0 10px; border-radius: 18px;
      display: inline-flex; align-items: center; justify-content: center; color: var(--fg);
      font: 600 14px/1 system-ui, "Noto Sans JP", sans-serif; cursor: pointer;
      background: color-mix(in srgb, var(--fg) 8%, transparent);
    }
    .bar button:active { background: color-mix(in srgb, var(--fg) 18%, transparent); }
    article {
      box-sizing: border-box; max-width: 42em; margin: 0 auto; padding: 20px 20px 120px;
      font-size: var(--size, 19px); line-height: 1.85; overflow-wrap: anywhere; word-break: normal;
      font-family: system-ui, "Noto Sans CJK JP", "Noto Sans JP", sans-serif;
    }
    article.serif { font-family: "Noto Serif CJK JP", "Noto Serif JP", "Source Han Serif", Georgia, serif; }
    .site { color: var(--dim); font-size: .8em; margin: 0 0 .4em; }
    h1.title { font-size: 1.55em; line-height: 1.4; margin: 0 0 .5em; }
    .meta { color: var(--dim); font-size: .82em; padding-bottom: 1.1em; margin-bottom: 1.4em; border-bottom: 1px solid var(--line); }
    .content h1, .content h2, .content h3, .content h4 { line-height: 1.45; margin: 1.6em 0 .6em; }
    .content h1 { font-size: 1.35em; } .content h2 { font-size: 1.25em; } .content h3 { font-size: 1.12em; }
    .content p { margin: 0 0 1.15em; }
    .content a { color: var(--link); }
    .content img, .content video, .content svg, .content picture { max-width: 100%; height: auto; border-radius: 8px; }
    .content figure { margin: 1.4em 0; }
    .content figcaption { color: var(--dim); font-size: .82em; text-align: center; margin-top: .4em; }
    .content blockquote { margin: 1.2em 0; padding: .2em 0 .2em 1em; border-left: 3px solid var(--line); color: var(--dim); }
    .content pre { white-space: pre-wrap; background: color-mix(in srgb, var(--fg) 6%, transparent); padding: .8em 1em; border-radius: 8px; font-size: .82em; }
    .content code { font-family: ui-monospace, "Roboto Mono", monospace; font-size: .9em; }
    .content table { display: block; overflow-x: auto; border-collapse: collapse; font-size: .9em; }
    .content td, .content th { border: 1px solid var(--line); padding: .35em .6em; }
    .content hr { border: none; border-top: 1px solid var(--line); margin: 2em 0; }
    .content ul, .content ol { padding-left: 1.4em; }
  `;

  function applyPrefs() {
    if (!root) return;
    const wrap = root.querySelector(".wrap");
    const article = root.querySelector("article");
    wrap.className = "wrap " + prefs.theme;
    article.className = prefs.font === "serif" ? "serif" : "";
    article.style.setProperty("--size", prefs.size + "px");
    root.querySelector(".theme").textContent = L(THEME_LABELS[prefs.theme] || "自動");
    root.querySelector(".font").textContent = L(prefs.font === "serif" ? "明朝" : "ゴシック");
    // ページ側の余白(本文より短いページの下側など)もリーダーの背景色にする
    requestAnimationFrame(() => {
      document.documentElement.style.setProperty("--aozora-reader-bg", getComputedStyle(wrap).backgroundColor);
    });
  }

  function button(cls, text, title, action) {
    const b = document.createElement("button");
    b.className = cls;
    b.textContent = text;
    b.title = title;
    b.setAttribute("aria-label", title);
    b.addEventListener("click", (e) => {
      e.preventDefault();
      e.stopPropagation();
      action();
    });
    return b;
  }

  function open() {
    if (host) return true;
    let article;
    try {
      article = parse();
    } catch (e) {
      article = null;
    }
    if (!article || !article.content || (article.textContent || "").trim().length < 140) {
      notify(false, "unavailable");
      return false;
    }

    savedScrollY = window.scrollY;
    if (!document.getElementById(STYLE_ID)) {
      const style = document.createElement("style");
      style.id = STYLE_ID;
      style.textContent = PAGE_CSS;
      (document.head || document.documentElement).appendChild(style);
    }

    host = document.createElement("aozora-reader");
    root = host.attachShadow({ mode: "closed" });
    const style = document.createElement("style");
    style.textContent = READER_CSS;
    root.appendChild(style);

    const wrap = document.createElement("div");
    wrap.className = "wrap";
    const bar = document.createElement("div");
    bar.className = "bar";
    bar.appendChild(button("close", "✕", L("リーディングモードを終了"), close));
    const label = document.createElement("span");
    label.className = "label";
    label.textContent = L("リーディングモード");
    bar.appendChild(label);
    bar.appendChild(button("smaller", "A−", L("文字を小さく"), () => {
      prefs.size = Math.max(13, prefs.size - 1);
      applyPrefs();
      savePrefs();
    }));
    bar.appendChild(button("larger", "A＋", L("文字を大きく"), () => {
      prefs.size = Math.min(32, prefs.size + 1);
      applyPrefs();
      savePrefs();
    }));
    bar.appendChild(button("font", L("ゴシック"), L("書体を切り替え"), () => {
      prefs.font = prefs.font === "serif" ? "sans" : "serif";
      applyPrefs();
      savePrefs();
    }));
    bar.appendChild(button("theme", L("自動"), L("背景色を切り替え"), () => {
      prefs.theme = THEMES[(THEMES.indexOf(prefs.theme) + 1) % THEMES.length];
      applyPrefs();
      savePrefs();
    }));
    wrap.appendChild(bar);

    const art = document.createElement("article");
    const siteName = article.siteName || location.hostname;
    if (siteName) {
      const site = document.createElement("div");
      site.className = "site";
      site.textContent = siteName;
      art.appendChild(site);
    }
    const h1 = document.createElement("h1");
    h1.className = "title";
    h1.textContent = article.title || document.title || "";
    art.appendChild(h1);
    const meta = document.createElement("div");
    meta.className = "meta";
    const parts = [];
    if (article.byline) parts.push(article.byline);
    parts.push(L("約 ") + readingMinutes(article.textContent || "") + L(" 分で読めます"));
    meta.textContent = parts.join(" ・ ");
    art.appendChild(meta);

    const content = document.createElement("div");
    content.className = "content";
    const body = sanitize(article.content);
    for (const node of Array.from(body.childNodes)) content.appendChild(document.importNode(node, true));
    art.appendChild(content);
    if (article.lang) art.setAttribute("lang", article.lang);
    if (article.dir) art.setAttribute("dir", article.dir);
    wrap.appendChild(art);
    root.appendChild(wrap);

    (document.body || document.documentElement).appendChild(host);
    document.documentElement.classList.add(ROOT_CLASS);
    applyPrefs();
    window.scrollTo(0, 0);
    notify(true);
    return true;
  }

  function close() {
    if (!host) return;
    const h = host;
    const wrap = root.querySelector(".wrap");
    host = null;
    root = null;
    notify(false);
    const finish = () => {
      h.remove();
      document.documentElement.classList.remove(ROOT_CLASS);
      document.documentElement.style.removeProperty("--aozora-reader-bg");
      window.scrollTo(0, savedScrollY);
    };
    wrap.classList.add("closing");
    setTimeout(finish, 190);
  }

  function toggle() {
    if (host) close();
    else open();
  }

  function notify(active, error) {
    if (onChange) onChange(active, error || null);
  }

  return {
    open: open,
    close: close,
    toggle: toggle,
    isOpen: () => host !== null,
    setOnChange: (fn) => { onChange = fn; },
  };
})();
