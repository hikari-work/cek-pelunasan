/**
 * app.js — Controller utama routing antar screen.
 * Menginisialisasi Telegram WebApp, memanggil auth, dan mengelola navigasi.
 */

const App = (() => {
  let currentService = null;
  let screenHistory = [];

  const THEME_KEY = 'miniapp_theme';
  const ICONS = { light: '☀', dark: '☾' };

  const screens = {
    loading: document.getElementById('screen-loading'),
    home:    document.getElementById('screen-home'),
    search:  document.getElementById('screen-search'),
    detail:  document.getElementById('screen-detail'),
  };

  const tg = window.Telegram?.WebApp;

  function initTheme() {
    const saved = localStorage.getItem(THEME_KEY);
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    const theme = saved || (prefersDark ? 'dark' : 'light');
    applyTheme(theme);
  }

  function applyTheme(theme) {
    document.documentElement.setAttribute('data-theme', theme);
    const btn = document.getElementById('theme-toggle');
    if (btn) btn.textContent = theme === 'dark' ? ICONS.light : ICONS.dark;
    localStorage.setItem(THEME_KEY, theme);
  }

  function toggleTheme() {
    const current = document.documentElement.getAttribute('data-theme') || 'light';
    applyTheme(current === 'dark' ? 'light' : 'dark');
  }

  function init() {
    initTheme();

    if (tg) {
      tg.ready();
      tg.expand();
      applyTelegramTheme();
    }

    Auth.start()
      .then(() => showScreen('home'))
      .catch(err => {
        showError(err.message || 'Gagal memverifikasi akses');
        document.querySelector('#screen-loading .loading-text').textContent =
          '⛔ Akses ditolak. Daftarkan diri ke admin bot.';
      });

    setupBackButton();
  }

  function applyTelegramTheme() {
    const params = tg.themeParams;
    if (!params) return;
    const root = document.documentElement;
    if (params.bg_color)          root.style.setProperty('--color-bg', params.bg_color);
    if (params.text_color)        root.style.setProperty('--color-text', params.text_color);
    if (params.hint_color)        root.style.setProperty('--color-muted', params.hint_color);
    if (params.secondary_bg_color) {
      root.style.setProperty('--color-white', params.secondary_bg_color);
    }
  }

  function showScreen(name, pushHistory = true) {
    Object.values(screens).forEach(s => s.classList.remove('active'));
    if (screens[name]) {
      screens[name].classList.add('active');
    }
    if (pushHistory && name !== 'loading') {
      screenHistory.push(name);
    }
    updateBackButton();
  }

  function goBack() {
    screenHistory.pop(); // remove current
    const prev = screenHistory[screenHistory.length - 1];
    if (prev) {
      showScreen(prev, false);
    } else {
      showScreen('home', false);
      screenHistory = ['home'];
    }
  }

  function setupBackButton() {
    if (!tg) return;
    tg.BackButton.onClick(() => {
      if (screenHistory.length > 1) {
        goBack();
      } else {
        tg.close();
      }
    });
  }

  function updateBackButton() {
    if (!tg) return;
    if (screenHistory.length > 1) {
      tg.BackButton.show();
    } else {
      tg.BackButton.hide();
    }
  }

  function openService(service) {
    currentService = service;
    Search.reset(service);
    showScreen('search');
  }

  function openDetail(service, id) {
    showScreen('detail');
    loadDetail(service, id);
  }

  function loadDetail(service, id) {
    const loadingEl = document.getElementById('detail-loading');
    const contentEl = document.getElementById('detail-content');
    loadingEl.classList.remove('hidden');
    contentEl.classList.add('hidden');
    contentEl.innerHTML = '';

    const token = sessionStorage.getItem('miniapp_token');
    fetch(`/api/mini/${service}/${encodeURIComponent(id)}`, {
      headers: { 'X-Mini-Token': token }
    })
      .then(res => {
        if (!res.ok) throw new Error('Data tidak ditemukan');
        return res.json();
      })
      .then(data => {
        loadingEl.classList.add('hidden');
        contentEl.classList.remove('hidden');
        if (service === 'tagihan')        Tagihan.renderDetail(data, contentEl);
        else if (service === 'pelunasan') Pelunasan.renderDetail(data, contentEl);
        else if (service === 'tabungan')  Tabungan.renderDetail(data, contentEl);
        else if (service === 'canvas')    Canvas.renderDetail(data, contentEl);
        else if (service === 'kolektas')  Kolektas.renderDetail(data, contentEl);
      })
      .catch(err => {
        loadingEl.classList.add('hidden');
        showError(err.message);
        goBack();
      });
  }

  function showError(msg, duration = 3000) {
    const toast = document.getElementById('error-toast');
    const msgEl = document.getElementById('error-toast-msg');
    msgEl.textContent = msg;
    toast.classList.remove('hidden');
    setTimeout(() => toast.classList.add('hidden'), duration);
  }

  function getToken() {
    return sessionStorage.getItem('miniapp_token');
  }

  function getCurrentService() {
    return currentService;
  }

  return { init, showScreen, openService, openDetail, showError, goBack, getToken, getCurrentService, toggleTheme };
})();

// Start
document.addEventListener('DOMContentLoaded', App.init);
