// 内蔵ページの英語表示。アプリが英語表示の時は、ページの URL に ?hl=en が付いて開かれる
// (GeckoEngine.toEngineUrl)。日本語の文字を下の表で置き換える。
(function () {
  "use strict";
  var hl = new URLSearchParams(location.search).get("hl");
  if (hl !== "en") return;
  var EN = {
    "新しいタブ": "New tab",
    "シークレットタブ": "Incognito tab",
    "シークレット モードです": "You've gone incognito",
    "検索または URL を入力": "Search or type URL",
    "このデバイスを使用する他のユーザーには、シークレット モードでのアクティビティは表示されません。 ただし、ダウンロードしたファイルとブックマークは保存されます。":
      "Other people who use this device won't see your incognito activity. However, downloads and bookmarks will be saved.",
    "AirSky で保存されない情報": "AirSky won't save",
    "閲覧履歴": "Your browsing history",
    "Cookie とサイトデータ": "Cookies and site data",
    "フォームに入力した情報": "Information entered in forms",
    "開いていたタブとそのサムネイル": "Open tabs and their thumbnails",
    "以下にはアクティビティが表示される可能性があります": "Your activity might still be visible to",
    "アクセスするウェブサイト": "Websites you visit",
    "勤務先や学校、教育機関": "Your employer, school, or institution",
    "インターネット サービス プロバイダ": "Your internet service provider",
    "すべてのシークレット タブを閉じると": "When you close all incognito tabs",
    "、このモードで開いたページの Cookie やサイトデータは消去されます。 シークレット タブを開いている間は、スクリーンショットや画面共有で画面が見られないように保護されます。":
      ", cookies and site data from pages opened in this mode are deleted. While incognito tabs are open, the screen is protected from screenshots and screen sharing.",
  };
  function norm(s) {
    return s.replace(/\s+/g, " ").trim();
  }
  function tr(s) {
    var key = norm(s);
    return Object.prototype.hasOwnProperty.call(EN, key) ? EN[key] : null;
  }
  document.documentElement.lang = "en";
  var t = tr(document.title);
  if (t) document.title = t;
  var walker = document.createTreeWalker(document.body || document.documentElement, NodeFilter.SHOW_TEXT);
  var node;
  while ((node = walker.nextNode())) {
    var v = tr(node.nodeValue);
    if (v != null) {
      var lead = /^\s/.test(node.nodeValue) ? " " : "";
      var trail = /\s$/.test(node.nodeValue) ? " " : "";
      node.nodeValue = lead + v + trail;
    }
  }
  var inputs = document.querySelectorAll("[placeholder],[title],[aria-label]");
  for (var i = 0; i < inputs.length; i++) {
    ["placeholder", "title", "aria-label"].forEach(function (a) {
      var s = inputs[i].getAttribute(a);
      var v = s && tr(s);
      if (v) inputs[i].setAttribute(a, v);
    });
  }
})();
