/**
 * auth.js — Autentikasi Mini App menggunakan initData Telegram.
 * Mengirim initData ke backend, menyimpan token ke sessionStorage.
 */

const Auth = (() => {

  function start() {
    const tg = window.Telegram?.WebApp;

    console.log('[Auth] tg exists:', !!tg);
    console.log('[Auth] tg.initData length:', tg?.initData?.length ?? 'N/A');
    console.log('[Auth] tg.initData (first 100):', tg?.initData?.substring(0, 100));

    // Mode dev: jika tidak ada Telegram WebApp (misalnya buka di browser biasa)
    if (!tg || !tg.initData) {
      console.warn('[Auth] Tidak ada initData, reject');
      return Promise.reject(new Error('Hanya bisa diakses melalui Telegram'));
    }

    const cachedToken = sessionStorage.getItem('miniapp_token');
    console.log('[Auth] cachedToken exists:', !!cachedToken);

    if (cachedToken) {
      // Verifikasi token masih valid dengan ping sederhana
      return fetch('/api/mini/tagihan/search?q=__ping__&page=0', {
        headers: { 'X-Mini-Token': cachedToken }
      }).then(res => {
        console.log('[Auth] ping status:', res.status);
        if (res.status === 401) {
          sessionStorage.removeItem('miniapp_token');
          return doAuth(tg.initData);
        }
        // Token masih valid
        return Promise.resolve();
      }).catch(() => doAuth(tg.initData));
    }

    return doAuth(tg.initData);
  }

  function doAuth(initData) {
    return fetch('/api/mini/auth', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ initData })
    }).then(res => {
      if (res.status === 401) throw new Error('initData tidak valid');
      if (res.status === 403) throw new Error('Anda belum terdaftar. Hubungi admin bot untuk mendapatkan akses.');
      if (!res.ok) throw new Error('Gagal autentikasi');
      return res.json();
    }).then(data => {
      sessionStorage.setItem('miniapp_token', data.token);
      sessionStorage.setItem('miniapp_user', JSON.stringify(data.user));
      return data;
    });
  }

  return { start };
})();
