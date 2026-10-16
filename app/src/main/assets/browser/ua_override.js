// サイトごとの互換性対策(Firefox の webcompat 介入と同じ考え方)。
// 対応ブラウザを User-Agent で決め打ちしているサイトに、Android 版 Chrome の UA を見せる。
//
// Yahoo! JAPAN の動画プレイヤー(YVP)は Android では Chrome 等しか許可しておらず、
// Firefox / GeckoView の UA だと「エラーコード：1001100 ご利用の環境では映像を視聴できません」になる。
// プレイヤー本体は s.yimg.jp の iframe の中でも同じ判定をするので、そちらにも適用する。
// 通信の User-Agent ヘッダーは background.js で書き換え、ページの navigator.userAgent はここで変える。
(function () {
  "use strict";
  const CHROME_UA =
    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
    "Chrome/141.0.0.0 Mobile Safari/537.36";
  try {
    const nav = window.navigator.wrappedJSObject;
    const define = (name, value) =>
      Object.defineProperty(nav, name, {
        get: exportFunction(() => value, window),
        configurable: true,
      });
    define("userAgent", CHROME_UA);
    define("appVersion", CHROME_UA.substring("Mozilla/".length));
    define("vendor", "Google Inc.");
  } catch (e) {
    // 上書きできなくてもページの表示は続ける
  }
})();
