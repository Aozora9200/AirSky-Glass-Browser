
  const patterns = ['snow', 'feather', 'petal', 'bubble', 'leaf', 'star', 'cloud', 'sparkle'];
  const patternSymbols = {
    snow: '❄',
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
    const isOpen = engineSelector.style.display === 'flex';
    if (!isOpen) {
      engineSelector.style.display = 'flex';
      engineSelector.style.flexDirection = 'column';
    } else {
      engineSelector.style.display = 'none';
    }
    engineToggle.setAttribute('aria-expanded', String(!isOpen));
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
      engineToggle.setAttribute('aria-expanded', 'false');
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
newsList.innerHTML = "<li>読み込み中…</li>";

fetch("https://api.rss2json.com/v1/api.json?rss_url=https://news.google.com/rss?hl=ja&gl=JP&ceid=JP:ja")
  .then(response => response.json())
  .then(data => {
    newsList.innerHTML = "";
    data.items.slice(0, 8).forEach(item => {
      const li = document.createElement("li");

      // サムネイル画像（なければデフォルト）
      const imgSrc = item.thumbnail || "https://www.gstatic.com/marketing-cms/assets/images/4b/1d/dcc752ee4ba4a56308fad264037f/google-news.webp=s48-fcrop64=1,00000000ffffffff-rw";

      li.innerHTML = `
        <img src="${imgSrc}" alt="news thumbnail">
        <a href="${item.link}">${item.title}</a>
      `;

      newsList.appendChild(li);
    });
  })
  .catch(error => {
    console.error(error);
    newsList.innerHTML = "<li>ニュースを読み込めませんでした。</li>";
  });
}
loadNews();

  const bgSelector = document.getElementById('bgSelector');
  const bgClear = document.getElementById('bgClear');
  const MAX_SIZE_LOWER = 2 * 1024 * 1024;
  const MAX_SIZE_UPPER = 8 * 1024 * 1024;

  addEventListener('DOMContentLoaded', () => {
    const savedBg = localStorage.getItem('bgImage');
    const isDark = window.matchMedia('(prefers-color-scheme: dark)').matches;

    if (isDark) {
      // 🌙 ダークモードのときの背景
      if (savedBg) document.body.style.backgroundImage = `linear-gradient(to bottom, rgba(0, 0, 0, 0.3) 0%, rgba(0, 0, 0, 0.7) 75%, #000 100%), url(${savedBg})`;
    } else {
      // ☀ ライトモードのときの背景
      if (savedBg) document.body.style.backgroundImage = `linear-gradient(to bottom, rgba(255, 255, 255, 0.3) 0%, rgba(255, 255, 255, 0.7) 75%, #fff 100%), url(${savedBg})`;
    }
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
        disableAutoWallpaper();
        const isDark = window.matchMedia('(prefers-color-scheme: dark)').matches;

        if (isDark) {
          // 🌙 ダークモードのときの背景
          document.body.style.backgroundImage = `linear-gradient(to bottom, rgba(0, 0, 0, 0.3) 0%, rgba(0, 0, 0, 0.7) 75%, #000 100%), url(${url})`;
        } else {
          // ☀ ライトモードのときの背景
          document.body.style.background = `linear-gradient(to bottom, rgba(255, 255, 255, 0.3) 0%, rgba(255, 255, 255, 0.7) 75%, #fff 100%), url(${url})`;
        }
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
      document.body.style.background = '';
      enableAutoWallpaper(true);
  });

  // ===== 追加：サイト追加機能 =====
  const CUSTOM_SITES_KEY = 'customSites';
  const iconGrid = document.getElementById('iconGrid');
  const addSiteBtn = document.getElementById('addSiteBtn');
  const editSitesBtn = document.getElementById('editSitesBtn');
  const siteModalOverlay = document.getElementById('siteModalOverlay');
  const siteNameInput = document.getElementById('siteNameInput');
  const siteUrlInput = document.getElementById('siteUrlInput');
  const siteModalPreview = document.getElementById('siteModalPreview');
  const siteModalPreviewImg = document.getElementById('siteModalPreviewImg');
  const siteModalPreviewText = document.getElementById('siteModalPreviewText');
  const siteModalCancel = document.getElementById('siteModalCancel');
  const siteModalSave = document.getElementById('siteModalSave');
  const siteModalError = document.getElementById('siteModalError');
  let editMode = false;

  function getCustomSites() {
    try {
      return JSON.parse(localStorage.getItem(CUSTOM_SITES_KEY)) || [];
    } catch (e) {
      return [];
    }
  }

  function saveCustomSites(sites) {
    localStorage.setItem(CUSTOM_SITES_KEY, JSON.stringify(sites));
  }

  // 入力されたURLからオリジン（サイトのトップURL）を安全に取り出す
  function normalizeUrl(raw) {
    let value = raw.trim();
    if (!value) return null;
    if (!/^https?:\/\//i.test(value)) {
      value = 'https://' + value;
    }
    try {
      const u = new URL(value);
      return u;
    } catch (e) {
      return null;
    }
  }

  // Googleのファビコン取得サービスを利用してアイコンURLを組み立てる
  function faviconUrlFor(origin) {
    return `https://t0.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=${encodeURIComponent(origin)}&size=64`;
  }

  function renderCustomSites() {
    // 既存の追加サイトタイルを一旦削除して再描画
    iconGrid.querySelectorAll('.icon-tile').forEach(el => el.remove());
    const sites = getCustomSites();
    sites.forEach((site, index) => {
      const tile = document.createElement('a');
      tile.href = site.url;
      tile.className = 'icon-tile' + (editMode ? ' editing' : '');
      tile.innerHTML = `
        <span class="remove-site" data-index="${index}">×</span>
        <img src="${site.icon}" alt="">
        <span>${site.name}</span>
      `;
      if (editMode) {
        tile.addEventListener('click', (e) => e.preventDefault());
      }
      iconGrid.insertBefore(tile, addSiteBtn);
    });
    iconGrid.querySelectorAll('.remove-site').forEach(btn => {
      btn.addEventListener('click', (e) => {
        e.preventDefault();
        e.stopPropagation();
        const idx = parseInt(btn.getAttribute('data-index'), 10);
        const sites = getCustomSites();
        sites.splice(idx, 1);
        saveCustomSites(sites);
        renderCustomSites();
      });
    });
  }
  renderCustomSites();

  editSitesBtn.addEventListener('click', () => {
    editMode = !editMode;
    editSitesBtn.textContent = editMode ? '編集を終了' : 'サイトを編集';
    renderCustomSites();
  });

  function openSiteModal() {
    siteNameInput.value = '';
    siteUrlInput.value = '';
    siteModalPreview.style.display = 'none';
    siteModalError.style.display = 'none';
    siteModalOverlay.style.display = 'flex';
    setTimeout(() => siteNameInput.focus(), 50);
  }
  function closeSiteModal() {
    siteModalOverlay.style.display = 'none';
  }

  addSiteBtn.addEventListener('click', openSiteModal);
  siteModalCancel.addEventListener('click', closeSiteModal);
  siteModalOverlay.addEventListener('click', (e) => {
    if (e.target === siteModalOverlay) closeSiteModal();
  });

  // URL入力に応じてファビコンをプレビュー
  let previewDebounce;
  siteUrlInput.addEventListener('input', () => {
    clearTimeout(previewDebounce);
    previewDebounce = setTimeout(() => {
      const u = normalizeUrl(siteUrlInput.value);
      if (!u) {
        siteModalPreview.style.display = 'none';
        return;
      }
      siteModalPreviewImg.src = faviconUrlFor(u.origin);
      siteModalPreviewText.textContent = u.hostname;
      siteModalPreview.style.display = 'flex';
    }, 300);
  });

  siteModalSave.addEventListener('click', () => {
    const u = normalizeUrl(siteUrlInput.value);
    if (!u) {
      siteModalError.textContent = '正しいURLを入力してください。';
      siteModalError.style.display = 'block';
      return;
    }
    const name = siteNameInput.value.trim() || u.hostname;
    const sites = getCustomSites();
    sites.push({
      name: name,
      url: u.href,
      icon: faviconUrlFor(u.origin)
    });
    saveCustomSites(sites);
    renderCustomSites();
    closeSiteModal();
  });

  siteUrlInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      siteModalSave.click();
    }
  });
  const WALLPAPER_FADE_MS = 2000; // フェード時間
  // hour から次の項目の hour まで有効。昇順で並べること（0:00〜4:00 は最後の枠が続く）
  const WALLPAPER_SCHEDULE = [
    { hour: 4,  src: 'assets/img/yugure.png' },      // 4:00〜6:00
    { hour: 6,  src: 'assets/img/bg-masthead.jpg' }, // 6:00〜16:00
    { hour: 16, src: 'assets/img/yugure.png' },      // 16:00〜18:00
    { hour: 18, src: 'assets/img/nightsky.png' }     // 18:00〜翌4:00
  ];

  const autoBgRoot = document.getElementById('autoBg');
  const autoLayers = [document.getElementById('bgLayerA'), document.getElementById('bgLayerB')];
  // body には filter アニメーションがあり、壁紙の色味や配置に影響するため <html> 直下へ移す
  document.documentElement.appendChild(autoBgRoot);
  autoBgRoot.style.setProperty('--wp-fade', WALLPAPER_FADE_MS + 'ms');

  let autoEnabled = false;
  let autoActive = 0;          // 前面にあるレイヤー番号
  let autoCurrentSrc = null;
  let autoLoadToken = 0;
  let autoTimer = null;
  let autoFadeTimer = null;
  let autoFadeInFirst = false; // 最初の1枚もフェードインさせるか

  function hasCustomWallpaper() {
    try { return !!localStorage.getItem('bgImage'); } catch (e) { return false; }
  }

  function getAutoWallpaper(date) {
    const h = date.getHours();
    let pick = WALLPAPER_SCHEDULE[WALLPAPER_SCHEDULE.length - 1];
    for (const e of WALLPAPER_SCHEDULE) if (h >= e.hour) pick = e;
    return pick.src;
  }

  function msUntilNextChange(now) {
    let min = Infinity;
    for (const e of WALLPAPER_SCHEDULE) {
      const t = new Date(now);
      t.setHours(e.hour, 0, 0, 0);
      if (t <= now) t.setDate(t.getDate() + 1);
      min = Math.min(min, t - now);
    }
    return min + 250;
  }

  // 進行中のフェードを即座に確定させる
  function finishAutoFade() {
    if (autoFadeTimer === null) return;
    clearTimeout(autoFadeTimer);
    autoFadeTimer = null;
    const cur = autoLayers[autoActive];
    const old = autoLayers[1 - autoActive];
    cur.style.transition = 'none';
    old.style.transition = 'none';
    old.classList.remove('visible');
    void old.offsetWidth;
    cur.style.transition = '';
    old.style.transition = '';
  }

  function swapAutoWallpaper(src) {
    finishAutoFade();
    const first = autoCurrentSrc === null;
    const prev = autoLayers[autoActive];
    const next = first ? prev : autoLayers[1 - autoActive];
    next.style.backgroundImage = `var(--bg-overlay), url("${src}")`;

    if (first) {
      if (!autoFadeInFirst) next.style.transition = 'none';
      void next.offsetWidth;
      next.classList.add('visible');
      if (!autoFadeInFirst) { void next.offsetWidth; next.style.transition = ''; }
      autoFadeInFirst = false;
    } else {
      // 新しい画像を前面に置いてフェードイン（下の旧画像は不透明のまま → 暗転しない）
      prev.style.zIndex = 1;
      next.style.zIndex = 2;
      void next.offsetWidth;
      next.classList.add('visible');
      autoActive = 1 - autoActive;
      autoFadeTimer = setTimeout(finishAutoFade, WALLPAPER_FADE_MS + 100);
    }
    autoCurrentSrc = src;
  }

  function showAutoWallpaper(src) {
    if (!autoEnabled || src === autoCurrentSrc) return;
    const token = ++autoLoadToken;
    const img = new Image();
    const proceed = (loaded) => {
      if (!autoEnabled || token !== autoLoadToken) return;
      // 読み込み失敗時は現状維持（次回の判定で再試行）。ただし最初の1枚だけはそのまま適用
      if (!loaded && autoCurrentSrc !== null) return;
      swapAutoWallpaper(src);
    };
    img.onload = () => proceed(true);
    img.onerror = () => proceed(false);
    img.src = src; // 読み込み完了後にフェード開始
  }

  function updateAutoWallpaper() {
    if (!autoEnabled) return;
    showAutoWallpaper(getAutoWallpaper(new Date()));
    clearTimeout(autoTimer);
    autoTimer = setTimeout(updateAutoWallpaper, msUntilNextChange(new Date()));
  }

  function enableAutoWallpaper(fadeIn = false) {
    autoEnabled = true;
    autoFadeInFirst = fadeIn && autoCurrentSrc === null;
    document.body.classList.add('auto-bg');
    autoBgRoot.classList.add('active');
    updateAutoWallpaper();
  }

  function disableAutoWallpaper() {
    autoEnabled = false;
    autoLoadToken++;
    clearTimeout(autoTimer); autoTimer = null;
    clearTimeout(autoFadeTimer); autoFadeTimer = null;
    document.body.classList.remove('auto-bg');
    autoBgRoot.classList.remove('active');
    autoLayers.forEach(l => {
      l.classList.remove('visible');
      l.style.backgroundImage = '';
      l.style.transition = '';
      l.style.zIndex = '';
    });
    autoActive = 0;
    autoCurrentSrc = null;
  }

  // バックグラウンドからの復帰時にも即座に判定（タイマーが止まっていても追従）
  document.addEventListener('visibilitychange', () => {
    if (!document.hidden) updateAutoWallpaper();
  });

  if (!hasCustomWallpaper()) enableAutoWallpaper();
