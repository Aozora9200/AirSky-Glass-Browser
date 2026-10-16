// Aozora <-> GeckoView ブリッジ（各ページに注入されるコンテンツスクリプト）
// WebView 時代の evaluateJavascript / JavascriptInterface の代わりに、
// ネイティブ側(GeckoTabView)とポートでメッセージをやり取りする。
(function () {
  "use strict";
  if (window.top !== window) return;

  let port;
  try {
    port = browser.runtime.connectNative("aozora");
  } catch (e) {
    return;
  }

  function iconUrl() {
    const link = document.querySelector(
      'link[rel~="icon"], link[rel="shortcut icon"], link[rel="apple-touch-icon"]'
    );
    if (link && link.href) return link.href;
    if (location.protocol === "http:" || location.protocol === "https:") {
      return location.origin + "/favicon.ico";
    }
    return "";
  }

  // ページが翻訳を断っているか(Chrome と同じく <meta name="google" content="notranslate">、
  // <html translate="no">、<html class="notranslate"> を見る)
  function noTranslate() {
    const html = document.documentElement;
    if (html && (html.getAttribute("translate") || "").toLowerCase() === "no") return true;
    if (html && html.classList.contains("notranslate")) return true;
    if (document.body && (document.body.getAttribute("translate") || "").toLowerCase() === "no") return true;
    for (const m of document.querySelectorAll('meta[name="google" i], meta[name="googlebot" i]')) {
      if (/notranslate/i.test(m.getAttribute("content") || "")) return true;
    }
    return false;
  }

  function reportPage() {
    port.postMessage({
      type: "page",
      url: location.href,
      title: document.title,
      icon: iconUrl(),
      noTranslate: noTranslate(),
    });
  }

  function negapoji() {
    document.documentElement.style.filter = "invert(1)";
    for (const img of document.getElementsByTagName("img")) {
      img.style.filter = "invert(1)";
    }
  }

  // ---- ページ保存(オフラインでも同じ見た目で開けるように 1 つの HTML にまとめる) ----

  // 埋め込む画像・CSS 内の画像の合計上限(これを超えた分は元の URL のまま残す)
  const SNAPSHOT_BUDGET = 15 * 1024 * 1024;
  const SNAPSHOT_MAX_ITEM = 3 * 1024 * 1024;

  function absUrl(u, base) {
    try {
      return new URL(u, base).href;
    } catch (e) {
      return u;
    }
  }

  function blobToDataUrl(blob) {
    return new Promise((resolve) => {
      const r = new FileReader();
      r.onload = () => resolve(r.result);
      r.onerror = () => resolve(null);
      r.readAsDataURL(blob);
    });
  }

  // 拡張機能のコンテンツスクリプトの fetch はホスト権限があるのでクロスオリジンでも読める
  async function fetchText(url) {
    try {
      const res = await fetch(url, { cache: "force-cache", credentials: "include" });
      return res.ok ? await res.text() : null;
    } catch (e) {
      return null;
    }
  }

  function makeInliner() {
    let used = 0;
    const cache = new Map();
    return async function inline(url) {
      if (!url || url.startsWith("data:")) return url;
      if (!/^https?:/i.test(url)) return url;
      if (cache.has(url)) return cache.get(url);
      let out = url;
      try {
        const res = await fetch(url, { cache: "force-cache", credentials: "include" });
        if (res.ok) {
          const blob = await res.blob();
          if (blob.size <= SNAPSHOT_MAX_ITEM && used + blob.size <= SNAPSHOT_BUDGET) {
            const dataUrl = await blobToDataUrl(blob);
            if (dataUrl) {
              used += blob.size;
              out = dataUrl;
            }
          }
        }
      } catch (e) {
        // 読めない画像は元の URL のまま(オンラインなら表示される)
      }
      cache.set(url, out);
      return out;
    };
  }

  // CSS 内の url(...) を絶対 URL にし、画像は埋め込む
  async function rewriteCssUrls(css, base, inline) {
    const re = /url\(\s*(['"]?)([^'")]+)\1\s*\)/g;
    const urls = new Set();
    let m;
    while ((m = re.exec(css))) {
      if (!m[2].startsWith("data:")) urls.add(m[2]);
    }
    const map = new Map();
    for (const u of urls) {
      const abs = absUrl(u, base);
      // フォントは大きい割に無くても読めるので埋め込まない
      map.set(u, /\.(woff2?|ttf|otf|eot)(\?|#|$)/i.test(abs) ? abs : await inline(abs));
    }
    return css.replace(re, (all, q, u) => (map.has(u) ? 'url("' + map.get(u) + '")' : all));
  }

  // 1 枚のスタイルシートの中身を文字列で取り出す(@import も展開する)
  async function sheetText(sheet, inline, depth) {
    const base = sheet.href || location.href;
    let text = null;
    try {
      const parts = [];
      for (const rule of sheet.cssRules) {
        if (rule.type === CSSRule.IMPORT_RULE && rule.styleSheet && depth < 4) {
          const inner = await sheetText(rule.styleSheet, inline, depth + 1);
          const media = rule.media && rule.media.mediaText;
          parts.push(media ? "@media " + media + "{" + inner + "}" : inner);
        } else {
          parts.push(rule.cssText);
        }
      }
      text = parts.join("\n");
    } catch (e) {
      // 別オリジンの CSS は cssRules を読めないので取得し直す
      if (sheet.href) text = await fetchText(sheet.href);
    }
    if (text == null) return "";
    return rewriteCssUrls(text, base, inline);
  }

  async function snapshotHtml() {
    const inline = makeInliner();
    const liveImgs = Array.from(document.querySelectorAll("img"));
    const liveCanvases = Array.from(document.querySelectorAll("canvas"));
    const liveFields = Array.from(document.querySelectorAll("input, textarea, select"));

    const root = document.documentElement.cloneNode(true);
    const cloneImgs = Array.from(root.querySelectorAll("img"));
    const cloneCanvases = Array.from(root.querySelectorAll("canvas"));
    const cloneFields = Array.from(root.querySelectorAll("input, textarea, select"));

    // 入力中の値を残す
    cloneFields.forEach((el, i) => {
      const live = liveFields[i];
      if (!live) return;
      if (el.tagName === "TEXTAREA") {
        el.textContent = live.value;
      } else if (el.tagName === "SELECT") {
        Array.from(el.options).forEach((o, j) => {
          if (live.options[j] && live.options[j].selected) o.setAttribute("selected", "");
          else o.removeAttribute("selected");
        });
      } else if (live.type === "checkbox" || live.type === "radio") {
        if (live.checked) el.setAttribute("checked", "");
        else el.removeAttribute("checked");
      } else if (live.type !== "password" && live.type !== "file") {
        el.setAttribute("value", live.value);
      }
    });

    // canvas は画像に置き換える
    cloneCanvases.forEach((el, i) => {
      try {
        const img = document.createElement("img");
        img.src = liveCanvases[i].toDataURL();
        img.setAttribute("style", el.getAttribute("style") || "");
        img.className = el.className;
        img.width = liveCanvases[i].width;
        img.height = liveCanvases[i].height;
        el.replaceWith(img);
      } catch (e) {
        // 別オリジンの画像を描いた canvas は読めない
      }
    });

    // 画像を埋め込む(表示中の解像度の画像を使う)
    for (let i = 0; i < cloneImgs.length; i++) {
      const el = cloneImgs[i];
      const live = liveImgs[i];
      const src = live ? live.currentSrc || live.src : el.getAttribute("src");
      if (src) el.setAttribute("src", await inline(absUrl(src, location.href)));
      el.removeAttribute("srcset");
      el.removeAttribute("sizes");
      el.removeAttribute("loading");
    }
    root.querySelectorAll("picture source").forEach((s) => s.remove());

    // CSS を 1 つの <style> にまとめる(読み込み順を保つ)
    const cssParts = [];
    for (const sheet of Array.from(document.styleSheets)) {
      if (sheet.disabled) continue;
      const text = await sheetText(sheet, inline, 0);
      const media = sheet.media && sheet.media.mediaText;
      cssParts.push(media && media !== "all" ? "@media " + media + "{" + text + "}" : text);
    }
    root.querySelectorAll('style, link[rel~="stylesheet"]').forEach((e) => e.remove());

    // インラインの style 属性内の画像
    for (const el of Array.from(root.querySelectorAll("[style*='url(']"))) {
      el.setAttribute("style", await rewriteCssUrls(el.getAttribute("style"), location.href, inline));
    }

    // スクリプトは保存しない(オフラインの file:// では動かず、ページを真っ白にすることがある)
    root.querySelectorAll(
      'script, link[rel="preload"], link[rel="modulepreload"], link[rel="prefetch"], ' +
        'link[rel="dns-prefetch"], link[rel="preconnect"], meta[http-equiv], base'
    ).forEach((e) => e.remove());
    // スクリプト無しの時の表示(<noscript>)は中身を出す。ただし「JavaScript を有効に」系の案内は不要なので
    // 画像などの実コンテンツを含むものだけ残す
    root.querySelectorAll("noscript").forEach((ns) => {
      const t = document.createElement("template");
      t.innerHTML = ns.textContent || "";
      if (t.content.querySelector("img, picture, video, iframe")) ns.replaceWith(t.content);
      else ns.remove();
    });
    root.querySelectorAll("*").forEach((el) => {
      for (const attr of Array.from(el.attributes)) {
        if (/^on/i.test(attr.name)) el.removeAttribute(attr.name);
        else if (/^\s*javascript:/i.test(attr.value) && /^(href|src|action)$/i.test(attr.name)) {
          el.removeAttribute(attr.name);
        }
      }
    });

    let head = root.querySelector("head");
    if (!head) {
      head = document.createElement("head");
      root.insertBefore(head, root.firstChild);
    }
    const style = document.createElement("style");
    style.textContent = cssParts.join("\n");
    head.appendChild(style);
    // リンクや埋め込めなかった画像は元のサイトを基準に開く
    const base = document.createElement("base");
    base.href = location.href;
    head.insertBefore(base, head.firstChild);
    const charset = document.createElement("meta");
    charset.setAttribute("charset", "utf-8");
    head.insertBefore(charset, head.firstChild);

    return "<!DOCTYPE html>\n" + root.outerHTML;
  }

  // ---- ボトムバーの周りの余白をページの色に合わせるため、画面下端に見えている背景色を知らせる ----

  // 色が付いていない(透明)なら null
  function opaqueColor(value) {
    const m = /rgba?\(([^)]+)\)/.exec(value || "");
    if (!m) return null;
    const parts = m[1].split(/[\s,\/]+/).filter(Boolean).map(Number);
    if (parts.length < 3) return null;
    const alpha = parts.length > 3 ? parts[3] : 1;
    if (!(alpha > 0.5)) return null;
    return "rgb(" + parts[0] + ", " + parts[1] + ", " + parts[2] + ")";
  }

  // 下端の中央にある要素から親へたどり、最初に背景色が付いている要素の色を使う
  function bottomColor() {
    const vv = window.visualViewport;
    const w = vv ? vv.width : window.innerWidth;
    const h = vv ? vv.height : window.innerHeight;
    const x = (vv ? vv.offsetLeft : 0) + w / 2;
    const y = (vv ? vv.offsetTop : 0) + h - 2;
    for (let el = document.elementFromPoint(x, y); el; el = el.parentElement) {
      const c = opaqueColor(getComputedStyle(el).backgroundColor);
      if (c) return c;
    }
    for (const el of [document.body, document.documentElement]) {
      if (!el) continue;
      const c = opaqueColor(getComputedStyle(el).backgroundColor);
      if (c) return c;
    }
    // 背景を指定していないページは既定のキャンバス色(ダーク対応のページでダーク表示なら暗い色)
    const scheme = getComputedStyle(document.documentElement).colorScheme || "";
    const dark = scheme.includes("dark") && window.matchMedia("(prefers-color-scheme: dark)").matches;
    return dark ? "rgb(28, 27, 34)" : "rgb(255, 255, 255)";
  }

  let lastBottomColor = null;
  let bottomColorTimer = 0;
  function reportBottomColor() {
    bottomColorTimer = 0;
    let color;
    try {
      color = bottomColor();
    } catch (e) {
      return;
    }
    if (color === lastBottomColor) return;
    lastBottomColor = color;
    port.postMessage({ type: "bottomColor", color: color });
  }
  // スクロール中は間引いて調べる
  function scheduleBottomColor() {
    if (!bottomColorTimer) bottomColorTimer = setTimeout(reportBottomColor, 150);
  }
  window.addEventListener("scroll", scheduleBottomColor, { passive: true });
  window.addEventListener("resize", scheduleBottomColor, { passive: true });
  window.addEventListener("load", scheduleBottomColor);
  if (window.visualViewport) {
    window.visualViewport.addEventListener("scroll", scheduleBottomColor, { passive: true });
    window.visualViewport.addEventListener("resize", scheduleBottomColor, { passive: true });
  }
  window.matchMedia("(prefers-color-scheme: dark)").addEventListener("change", scheduleBottomColor);

  // ページの一番下付近までスクロールしているか。ネイティブ側はこの時だけ表示領域を縮めて
  // ページの下端をバーの上に収める(縮める=リサイズはスクロール開始時に画面が一瞬乱れるため、
  // 必要な時以外はしない)。
  let lastNearBottom = null;
  let nearBottomTimer = 0;
  // ページ全体の高さ(画面の px)。画面より少しだけ長いページかどうかをネイティブ側が判断するのに使う
  let lastDocHeight = -1;
  function reportDocHeight(el) {
    const h = Math.round(el.scrollHeight * (window.devicePixelRatio || 1));
    if (Math.abs(h - lastDocHeight) <= 1) return;
    lastDocHeight = h;
    port.postMessage({ type: "docHeight", value: h });
  }
  function reportNearBottom() {
    nearBottomTimer = 0;
    const el = document.scrollingElement || document.documentElement;
    if (!el) return;
    reportDocHeight(el);
    const remaining = el.scrollHeight - (window.scrollY + window.innerHeight);
    const near = remaining <= 160;
    if (near === lastNearBottom) return;
    lastNearBottom = near;
    port.postMessage({ type: "nearBottom", value: near });
  }
  function scheduleNearBottom() {
    if (!nearBottomTimer) nearBottomTimer = setTimeout(reportNearBottom, 80);
  }
  window.addEventListener("scroll", scheduleNearBottom, { passive: true });
  window.addEventListener("resize", scheduleNearBottom, { passive: true });
  window.addEventListener("load", scheduleNearBottom);
  // 読み込みが進んでページの長さだけが変わった時も知らせる
  if (window.ResizeObserver && document.documentElement) {
    new ResizeObserver(scheduleNearBottom).observe(document.documentElement);
  }

  port.onMessage.addListener((msg) => {
    // アプリの表示言語(リーディングモードの表示に使う)
    if (msg && msg.lang) window.__airskyLang = msg.lang;
    switch (msg && msg.type) {
      case "getPage":
        reportPage();
        lastBottomColor = null;
        reportBottomColor();
        lastNearBottom = null;
        lastDocHeight = -1;
        reportNearBottom();
        break;
      case "negapoji":
        negapoji();
        break;
      case "getHtml":
        port.postMessage({
          type: "html",
          id: msg.id,
          html: "<!DOCTYPE html>\n" + document.documentElement.outerHTML,
        });
        break;
      case "readerToggle":
        if (reader) reader.toggle();
        else port.postMessage({ type: "reader", active: false, error: "unavailable" });
        break;
      case "readerClose":
        if (reader) reader.close();
        break;
      case "getSnapshot":
        snapshotHtml()
          .then((html) => port.postMessage({ type: "html", id: msg.id, html: html }))
          .catch(() => port.postMessage({ type: "html", id: msg.id, html: null }));
        break;
    }
  });

  // リーディングモード(reader.js)の開閉をネイティブ側へ知らせる(戻るキーで閉じるため)
  const reader = typeof AozoraReader !== "undefined" ? AozoraReader : null;
  if (reader) {
    reader.setOnChange((active, error) => {
      port.postMessage({ type: "reader", active: active, error: error });
      // 開閉のアニメーションが終わってから、画面下端の色を調べ直す
      [260, 700].forEach((ms) => setTimeout(() => {
        lastBottomColor = null;
        reportBottomColor();
      }, ms));
    });
  }

  reportPage();
  reportBottomColor();
  reportNearBottom();
})();
