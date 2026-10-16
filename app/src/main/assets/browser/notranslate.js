// ページが翻訳しないように指定しているか(<meta name="google" content="notranslate"> など)を、
// HTML を読み終わった時点ですぐアプリへ知らせるコンテンツスクリプト。
//
// bridge.js(document_idle)も同じことを知らせるが、大きな defer のスクリプトがあるページでは
// document_idle がかなり遅く、その前に自動翻訳(常に翻訳)が始まってしまうため、こちらで早めに知らせる。
(function () {
  "use strict";
  if (window.top !== window) return;

  function noTranslate() {
    const html = document.documentElement;
    if (html && (html.getAttribute("translate") || "").toLowerCase() === "no") return true;
    if (html && html.classList.contains("notranslate")) return true;
    if (document.body && (document.body.getAttribute("translate") || "").toLowerCase() === "no") return true;
    for (const m of document.querySelectorAll('meta[name="google" i], meta[name="googlebot" i]')) {
      if (/\bnotranslate\b/i.test(m.getAttribute("content") || "")) return true;
    }
    return false;
  }

  let sent = false;
  function check() {
    if (sent || document.readyState === "loading") return;
    sent = true;
    if (!noTranslate()) return;
    try {
      browser.runtime.sendNativeMessage("aozora", { type: "noTranslate", url: location.href });
    } catch (e) {}
  }

  // "interactive" は HTML を読み終えた時点(defer のスクリプトを動かす前)
  document.addEventListener("readystatechange", check);
  check();
})();
