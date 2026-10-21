// Aozora 組み込み拡張のバックグラウンド。
// ネイティブ側から設定を受け取り、「画像を読み込まない」設定を webRequest で実現する。
"use strict";

let blockImages = false;

const port = browser.runtime.connectNative("aozora_bg");
port.onMessage.addListener((msg) => {
  if (msg && msg.type === "settings") {
    blockImages = !!msg.blockImages;
  } else if (msg && msg.type === "coaraPlugins") {
    coaraQueue(() => coaraApplyPlugins(msg));
  } else if (msg && msg.type === "coaraStore") {
    coaraQueue(() => coaraApplyStore(msg.id, msg.v, msg.store));
  }
});

// ---- Coara Browser 拡張機能(プラグイン) ----
// アプリから届いたプラグインを、ページの読み込み開始時に動くコンテンツスクリプトとして登録する
// (実際に動かすのは coara_cs.js)。登録はプラグインごと・保存データごとに分ける。
const coaraPluginRegs = new Map(); // id → 登録
const coaraStoreRegs = new Map();  // id → 登録
let coaraChain = Promise.resolve();

function coaraQueue(task) {
  coaraChain = coaraChain.then(task).catch((e) => console.warn("Coara plugin", e));
}

async function coaraUnregister(reg) {
  if (!reg) return;
  try { await reg.unregister(); } catch (e) {}
}

async function coaraApplyStore(id, v, store) {
  if (!id) return;
  const code = "var __coaraStores=__coaraStores||{};(function(id,v,d){var c=__coaraStores[id];" +
    "if(!c||c.v<=v)__coaraStores[id]={v:v,data:d};" +
    "if(typeof __coaraOnStore===\"function\")__coaraOnStore(id);})(" +
    JSON.stringify(id) + "," + (Number(v) || 0) + "," + JSON.stringify(store || {}) + ");";
  const old = coaraStoreRegs.get(id);
  // 新しい方を先に登録してから古い方を外す(間に読み込んだページで保存データが空にならないように)
  const reg = await browser.contentScripts.register({
    matches: ["<all_urls>"],
    js: [{ code }],
    runAt: "document_start",
    allFrames: true,
  });
  coaraStoreRegs.set(id, reg);
  await coaraUnregister(old);
}

async function coaraApplyPlugins(msg) {
  const plugins = Array.isArray(msg.plugins) ? msg.plugins : [];
  const stores = msg.stores || {};
  const versions = msg.versions || {};
  const ids = new Set(plugins.map((p) => p.id));
  for (const [id, reg] of [...coaraPluginRegs]) {
    if (!ids.has(id)) {
      coaraPluginRegs.delete(id);
      await coaraUnregister(reg);
    }
  }
  for (const [id, reg] of [...coaraStoreRegs]) {
    if (!ids.has(id)) {
      coaraStoreRegs.delete(id);
      await coaraUnregister(reg);
    }
  }
  for (const p of plugins) {
    await coaraApplyStore(p.id, versions[p.id], stores[p.id]);
    const code = "var __coaraPending=__coaraPending||[];(function(p){" +
      "if(typeof __coaraBoot===\"function\")__coaraBoot(p);else __coaraPending.push(p);})(" +
      JSON.stringify(p) + ");";
    const old = coaraPluginRegs.get(p.id);
    const reg = await browser.contentScripts.register({
      matches: ["<all_urls>"],
      js: [{ code }],
      runAt: "document_start",
      allFrames: !!p.allFrames,
    });
    coaraPluginRegs.set(p.id, reg);
    await coaraUnregister(old);
  }
}

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
