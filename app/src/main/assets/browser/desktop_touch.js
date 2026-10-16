// デスクトップ版で表示している時に、ページからタッチ端末に見えないようにするコンテンツスクリプト。
//
// GeckoView はタッチ画面があるので、デスクトップの UA でも "ontouchstart" in window や
// navigator.maxTouchPoints からタッチ端末だと分かってしまう。パソコンのブラウザ(タッチ画面なし)には
// これらが無いので、これで「タッチ端末なら別の動き」をするページがデスクトップ版の動きにならない。
// 例: タッチ端末だとメニューの透明な層を閉じないブラウザゲームがあり、
// クリックがゲームに届かないのでマウスの固定(ポインターロック)が始まらなかった。
//
// UA がモバイル(Android / Mobile / iPhone / iPad)の時は何もしない。タッチのイベント自体は今までどおり届く
// (隠すのは、タッチ端末かどうかを調べるためのプロパティだけ)。
(function () {
  "use strict";
  const page = window.wrappedJSObject;
  if (!page) return;
  const ua = navigator.userAgent || "";
  if (/Android|Mobile|iPhone|iPad|iPod/i.test(ua)) return;

  const handlers = ["ontouchstart", "ontouchend", "ontouchmove", "ontouchcancel"];
  for (const name of ["Window", "Document", "HTMLElement", "Element", "SVGElement", "MathMLElement"]) {
    const ctor = page[name];
    const proto = ctor && ctor.prototype;
    if (!proto) continue;
    for (const h of handlers) {
      try {
        if (Object.prototype.hasOwnProperty.call(proto, h)) delete proto[h];
      } catch (e) {}
    }
  }
  try {
    for (const h of handlers) {
      if (Object.prototype.hasOwnProperty.call(page, h)) delete page[h];
    }
  } catch (e) {}
  try {
    Object.defineProperty(page.Navigator.prototype, "maxTouchPoints", {
      configurable: true,
      enumerable: true,
      get: exportFunction(function () {
        return 0;
      }, page),
    });
  } catch (e) {}
  // 古い API(document.createTouch / createTouchList)もパソコンには無い
  try {
    delete page.Document.prototype.createTouch;
    delete page.Document.prototype.createTouchList;
  } catch (e) {}
})();
