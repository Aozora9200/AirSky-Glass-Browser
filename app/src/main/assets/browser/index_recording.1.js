
    const patterns = ['snow', 'feather', 'petal', 'bubble', 'leaf', 'star', 'cloud', 'sparkle'];
    const patternSymbols = {
      snow: '',
      feather: '',
      petal: '',
      bubble: '',
      leaf: '',
      star: '',
      cloud: '',
      sparkle: '',
      bird: ''
    };
    const patternTop = {
      snow: "-10vh",
      feather: "110vh",
      petal: "-10vh",
      bubble: "110vh",
      leaf: "-10vh",
      star: "110vh",
      cloud: null,
      sparkle: null
    };
    function createFluffy() {
      const pattern = patterns[Math.floor(Math.random() * patterns.length)];
      const div = document.createElement('div');
      div.classList.add('fluffy', pattern);
      div.textContent = patternSymbols[pattern];
      div.style.left = Math.random() * 100 + 'vw';
      div.style.top = patternTop[pattern] ? patternTop[pattern] : Math.random() * 100 + 'vh';
      div.style.fontSize = (16 + Math.random() * 14) + 'px';
      const duration = 8 + Math.random() * 8;
      div.style.animationDuration = duration + 's';
      div.style.webkitAnimationDuration = duration + 's';
      document.body.appendChild(div);
      setTimeout(function() { div.remove(); }, duration * 1000);
    }
    setInterval(createFluffy, 500);
    function createBird() {
      const div = document.createElement('div');
      div.classList.add('fluffy', 'bird');
      div.textContent = patternSymbols['bird'];
      div.style.top = (10 + Math.random() * 60) + 'vh';
      div.style.fontSize = '24px';
      const duration = 12;
      div.style.animationDuration = duration + 's';
      div.style.webkitAnimationDuration = duration + 's';
      document.body.appendChild(div);
      setTimeout(function() { div.remove(); }, duration * 1000);
    }
    function scheduleBird() {
      const delay = 10000 + Math.random() * 20000;
      setTimeout(function() { createBird(); scheduleBird(); }, delay);
    }
    scheduleBird();
    const inputField = document.querySelector('input[type="text"]');
    inputField.addEventListener('focus', function() {
      inputField.style.transform = 'scale(1.05)';
      inputField.style.webkitTransform = 'scale(1.05)';
    });
    inputField.addEventListener('blur', function() {
      inputField.style.transform = 'scale(1)';
      inputField.style.webkitTransform = 'scale(1)';
    });

    // 検索エンジン選択機能
    const searchEngines = {
      google: { action: "https://www.google.com/search", param: "q", placeholder: "Googleで検索" },
      bing: { action: "https://www.bing.com/search", param: "q", placeholder: "Bingで検索" },
      yahoo: { action: "https://search.yahoo.co.jp/search", param: "p", placeholder: "Yahooで検索" },
      duckduckgo: { action: "https://duckduckgo.com/", param: "q", placeholder: "DuckDuckGoで検索" },
      ecosia: { action: "https://www.ecosia.org/search", param: "q", placeholder: "Ecosiaで検索" },
      youtube: { action: "https://www.youtube.com/results", param: "search_query", placeholder: "YouTubeで検索" }
    };
    const engineToggle = document.getElementById('engineToggle');
    const engineSelector = document.getElementById('engineSelector');
    const notification = document.getElementById('notification');
    const searchForm = document.getElementById('searchForm');
    const searchInput = document.getElementById('searchInput');

    // フォーム送信時、検索欄が空なら送信をキャンセル
    searchForm.addEventListener('submit', function(e) {
      if (!searchInput.value.trim()) {
        e.preventDefault();
        return false;
      }
    });

    engineToggle.addEventListener('click', () => {
      if (engineSelector.style.display === 'none' || engineSelector.style.display === '') {
        engineSelector.style.display = 'flex';
        engineSelector.style.flexDirection = 'column';
      } else {
        engineSelector.style.display = 'none';
      }
    });

    engineSelector.querySelectorAll('button').forEach(button => {
      button.addEventListener('click', () => {
        const engineKey = button.getAttribute('data-engine');
        if (searchEngines[engineKey]) {
          const engine = searchEngines[engineKey];
          searchForm.action = engine.action;
          searchInput.name = engine.param;
          searchInput.placeholder = engine.placeholder;
          notification.textContent = engineKey + " に変更しました";
          notification.style.display = 'block';
          setTimeout(() => { notification.style.display = 'none'; }, 2000);
        }
        engineSelector.style.display = 'none';
      });
    });

    const suggestBox = document.getElementById('suggestions');
let debounceTimer;

searchInput.addEventListener('input', () => {
  const query = searchInput.value.trim();
  clearTimeout(debounceTimer);
  if (query.length === 0) {
    suggestBox.style.display = 'none';
    return;
  }

  debounceTimer = setTimeout(() => {
    fetch(`https://suggestqueries.google.com/complete/search?client=firefox&q=${encodeURIComponent(query)}`)
      .then(res => res.json())
      .then(data => {
        const suggestions = data[1];
        if (suggestions.length === 0) {
          suggestBox.style.display = 'none';
          return;
        }

        suggestBox.innerHTML = '';
        suggestions.forEach(word => {
          const li = document.createElement('li');
          li.textContent = word;
          li.addEventListener('click', () => {
            searchInput.value = word;
            suggestBox.style.display = 'none';
            searchForm.submit();
          });
          suggestBox.appendChild(li);
        });
        suggestBox.style.display = 'block';
      });
  }, 300);
});

document.addEventListener('click', (e) => {
  if (!searchInput.contains(e.target) && !suggestBox.contains(e.target)) {
    suggestBox.style.display = 'none';
  }
});

    function loadNews() {
  const newsList = document.getElementById("newsList");
  newsList.innerHTML = "読み込み中…";

  fetch("https://api.rss2json.com/v1/api.json?rss_url=https://news.google.com/rss?hl=ja&gl=JP&ceid=JP:ja")
    .then(response => response.json())
    .then(data => {
      newsList.innerHTML = "";
      data.items.slice(0, 8).forEach(item => {
        const li = document.createElement("li");
        li.innerHTML = `<a href="${item.link}" target="_blank">${item.title}</a>`;
        newsList.appendChild(li);
      });
    })
    .catch(error => {
      console.error(error);
      newsList.innerHTML = "ニュースを読み込めませんでした。";
    });
}
    loadNews();

    const bgSelector = document.getElementById('bgSelector');
    const bgClear = document.getElementById('bgClear');
    const MAX_SIZE_LOWER = 2 * 1024 * 1024;
    const MAX_SIZE_UPPER = 8 * 1024 * 1024;

    addEventListener('DOMContentLoaded', () => {
      const savedBg = localStorage.getItem('bgImage');
      if (savedBg) document.body.style.backgroundImage = `url(${savedBg})`;
    });

    bgSelector.addEventListener('change', e => {
      const file = e.target.files[0];
      if (!file || file.size > MAX_SIZE_UPPER) {
        if (file && file.size > MAX_SIZE_UPPER) alert("8MBを超える画像はサポートしていません。");
        return;
      }

      const reader = new FileReader();
      reader.onload = event => {
        const dataUrl = event.target.result;
        const applyBackground = (url) => {
          localStorage.setItem('bgImage', url);
          document.body.style.backgroundImage = `url(${url})`;
          document.body.style.backgroundSize = 'cover';
          document.body.style.backgroundPosition = 'center';
      };

        if (file.size > MAX_SIZE_LOWER) {
          const img = new Image();
          img.onload = () => {
            const ratio = Math.sqrt(MAX_SIZE_LOWER / file.size);
            const targetWidth = Math.floor(img.width * ratio);
            const targetHeight = Math.floor(img.height * ratio);
            const canvas = document.createElement('canvas');
            canvas.width = targetWidth;
            canvas.height = targetHeight;
            const ctx = canvas.getContext('2d', { alpha: false });
            ctx.drawImage(img, 0, 0, targetWidth, targetHeight);
            const optimizedDataUrl = canvas.toDataURL('image/jpeg', 0.8);
            applyBackground(optimizedDataUrl);
          };
          img.src = dataUrl;
        } else {
          applyBackground(dataUrl);
        }
      };

      reader.readAsDataURL(file);
    });

    bgClear.addEventListener('click', () => {
      localStorage.removeItem('bgImage');
      document.body.style.backgroundImage = 'none';
      // ダークモード判定
      const isDark = window.matchMedia('(prefers-color-scheme: dark)').matches;

      if (isDark) {
        // 🌙 ダークモードのときの背景
        document.body.style.background = 'linear-gradient(135deg, #111111 0%, #444444 100%)';
      } else {
        // ☀ ライトモードのときの背景
        document.body.style.background = 'linear-gradient(135deg, #eeeeee 0%, #bbbbbb 100%)';
      }
    });
  