// ポインターロック(マウスの固定)をアプリ側で行うためのコンテンツスクリプト。
//
// GeckoView には Android のマウスの固定(pointer capture)の仕組みが無く、ページが requestPointerLock()
// を呼んでもマウスの動きを取れない。そこでページの requestPointerLock / exitPointerLock /
// document.pointerLockElement を置き換え、固定・解除をアプリへ伝える。固定している間、アプリは
// カーソルを隠し、マウス・タッチ・コントローラーの動きを "move" で送ってくるので、それを
// movementX / movementY の付いた mousemove / pointermove としてロックした要素へ送る。
// クリック・ホイールはアプリが固定した位置で本物のマウスのイベントとして送る。
(function () {
  "use strict";
  const page = window.wrappedJSObject;
  if (!page || !page.Element || !page.Document) return;

  let port = null;
  let lockEl = null;
  let lastX = 0;
  let lastY = 0;
  let buttons = 0;
  // ユーザーの操作の直後だけ固定を受け付ける(ブラウザと同じく、勝手に固定させない)
  let lastActivation = 0;

  for (const type of ["mousedown", "mouseup", "pointerdown", "pointerup", "keydown", "touchend", "click"]) {
    window.addEventListener(
      type,
      (e) => {
        if (e.isTrusted) lastActivation = Date.now();
        if (e.isTrusted && (type === "mousedown" || type === "mouseup")) buttons = e.buttons;
      },
      true
    );
  }
  window.addEventListener(
    "mousemove",
    (e) => {
      if (!e.isTrusted) return;
      lastX = e.clientX;
      lastY = e.clientY;
    },
    true
  );

  function connect() {
    if (port) return port;
    try {
      port = browser.runtime.connectNative("aozora_input");
    } catch (e) {
      port = null;
      return null;
    }
    port.onMessage.addListener(onNative);
    port.onDisconnect.addListener(() => {
      port = null;
      if (lockEl) unlock(false);
    });
    return port;
  }

  function post(msg) {
    const p = connect();
    if (!p) return false;
    try {
      p.postMessage(msg);
      return true;
    } catch (e) {
      return false;
    }
  }

  function fireDocument(type) {
    try {
      document.dispatchEvent(new Event(type, { bubbles: true }));
    } catch (e) {}
  }

  function lock(el) {
    if (lockEl === el) return;
    if (!post({ type: "lock" })) {
      fireDocument("pointerlockerror");
      return;
    }
    lockEl = el;
    fireDocument("pointerlockchange");
  }

  function unlock(notify) {
    if (!lockEl) return;
    lockEl = null;
    if (notify) post({ type: "unlock" });
    fireDocument("pointerlockchange");
  }

  function sendMove(dx, dy) {
    const el = lockEl && lockEl.isConnected ? lockEl : null;
    if (!el) {
      if (lockEl) unlock(true);
      return;
    }
    const init = {
      bubbles: true,
      cancelable: true,
      composed: true,
      view: window,
      clientX: lastX,
      clientY: lastY,
      screenX: lastX,
      screenY: lastY,
      movementX: Math.round(dx),
      movementY: Math.round(dy),
      buttons: buttons,
      pointerId: 1,
      pointerType: "mouse",
      isPrimary: true,
    };
    try {
      el.dispatchEvent(new PointerEvent("pointermove", init));
    } catch (e) {}
    try {
      el.dispatchEvent(new MouseEvent("mousemove", init));
    } catch (e) {}
  }

  // 小数の動きは次へ持ち越す(ゆっくり動かした時も止まらないように)
  let restX = 0;
  let restY = 0;

  function onNative(msg) {
    if (!msg) return;
    if (msg.type === "move") {
      const dx = msg.dx + restX;
      const dy = msg.dy + restY;
      const ix = Math.trunc(dx);
      const iy = Math.trunc(dy);
      restX = dx - ix;
      restY = dy - iy;
      if (ix !== 0 || iy !== 0) sendMove(ix, iy);
    } else if (msg.type === "unlock") {
      unlock(false);
    }
  }

  const requestLock = function (options) {
    const el = this;
    const ok = Date.now() - lastActivation < 5000;
    if (ok) {
      lock(el);
    } else {
      fireDocument("pointerlockerror");
    }
    try {
      return ok ? page.Promise.resolve() : page.Promise.reject(new page.DOMException("", "NotAllowedError"));
    } catch (e) {
      return undefined;
    }
  };

  try {
    exportFunction(requestLock, page.Element.prototype, { defineAs: "requestPointerLock" });
    exportFunction(
      function () {
        unlock(true);
      },
      page.Document.prototype,
      { defineAs: "exitPointerLock" }
    );
    Object.defineProperty(page.Document.prototype, "pointerLockElement", {
      configurable: true,
      enumerable: true,
      get: exportFunction(function () {
        return lockEl && lockEl.isConnected ? lockEl : null;
      }, page),
    });
  } catch (e) {}

  // 別のタブへ切り替えた・ページを隠した時は解除する
  document.addEventListener("visibilitychange", () => {
    if (document.hidden && lockEl) unlock(true);
  });
})();
