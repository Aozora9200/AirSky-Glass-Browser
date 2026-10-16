// シークレット タブのスタートページ: 検索欄に入れた語を検索、URL ならそのまま開く
(function () {
  var form = document.getElementById('searchForm');
  var input = document.getElementById('searchInput');
  if (!form || !input) return;

  function looksLikeUrl(text) {
    if (/^[a-z][a-z0-9+.-]*:\/\//i.test(text)) return true;
    // 空白を含まず、ドメインらしい形(example.com, example.com/path)
    return !/\s/.test(text) && /^[^\s/]+\.[a-z]{2,}(?::\d+)?(\/.*)?$/i.test(text);
  }

  form.addEventListener('submit', function (e) {
    var text = input.value.trim();
    e.preventDefault();
    if (!text) return;
    if (looksLikeUrl(text)) {
      location.href = /^[a-z][a-z0-9+.-]*:\/\//i.test(text) ? text : 'https://' + text;
    } else {
      location.href = 'https://www.google.com/search?q=' + encodeURIComponent(text);
    }
  });
})();
