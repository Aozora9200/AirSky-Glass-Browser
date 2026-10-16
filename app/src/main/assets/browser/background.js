// Aozora 組み込み拡張のバックグラウンド。
// ネイティブ側から設定を受け取り、「画像を読み込まない」設定を webRequest で実現する。
"use strict";

let blockImages = false;

const port = browser.runtime.connectNative("aozora_bg");
port.onMessage.addListener((msg) => {
  if (msg && msg.type === "settings") {
    blockImages = !!msg.blockImages;
  }
});

browser.webRequest.onBeforeRequest.addListener(
  () => (blockImages ? { cancel: true } : {}),
  { urls: ["<all_urls>"], types: ["image", "imageset"] },
  ["blocking"]
);

// サイトごとの互換性対策: User-Agent で対応ブラウザを決め打ちしているサイトに Chrome の UA を送る
// (ページ側の navigator.userAgent は ua_override.js で変える)。
const CHROME_UA =
  "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
  "Chrome/141.0.0.0 Mobile Safari/537.36";
browser.webRequest.onBeforeSendHeaders.addListener(
  (details) => {
    for (const h of details.requestHeaders) {
      if (h.name.toLowerCase() === "user-agent") h.value = CHROME_UA;
    }
    return { requestHeaders: details.requestHeaders };
  },
  { urls: ["*://*.yahoo.co.jp/*", "*://s.yimg.jp/images/yvpub/*"] },
  ["blocking", "requestHeaders"]
);
