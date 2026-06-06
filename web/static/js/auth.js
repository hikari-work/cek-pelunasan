/**
 * auth.js — Autentikasi Mini App menggunakan initData Telegram.
 * Mengirim initData ke backend, menyimpan token ke sessionStorage.
 */

const Auth = (() => {

  function checkPing(ip, port = 449, timeout = 1500) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeout);

    return fetch(`http://${ip}:${port}`, { mode: 'no-cors', signal: controller.signal })
      .then(() => {
        clearTimeout(timer);
        return true;
      })
      .catch((err) => {
        clearTimeout(timer);
        if (err.name === 'AbortError') {
          console.warn(`[Auth] Connection to ${ip}:${port} timed out.`);
          return false;
        }
        console.log(`[Auth] Connection to ${ip}:${port} active (resolved via error).`);
        return true;
      });
  }

  function start() {
    // Bypass auth jika diakses dari localhost
    const host = window.location.hostname;
    if (host === 'localhost' || host === '127.0.0.1') {
      console.log('[Auth] Localhost detected, bypassing auth');
      return Promise.resolve();
    }

    const tg = window.Telegram?.WebApp;

    console.log('[Auth] tg exists:', !!tg);
    console.log('[Auth] tg.initData length:', tg?.initData?.length ?? 'N/A');
    console.log('[Auth] tg.initData (first 100):', tg?.initData?.substring(0, 100));

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
          return proceedToAuth(tg);
        }
        // Token masih valid
        return Promise.resolve();
      }).catch(() => proceedToAuth(tg));
    }

    return proceedToAuth(tg);
  }

  function proceedToAuth(tg) {
    if (tg && tg.initData) {
      return doAuth(tg.initData);
    }

    console.log('[Auth] No Telegram initData, checking connection to AS400 (10.10.1.1:449)...');
    return checkPing('10.10.1.1', 449, 1500).then(canPing => {
      if (canPing) {
        console.log('[Auth] Intranet/VPN detected, requesting ping-bypass auth');
        return doPingBypassAuth();
      }
      throw new Error('Hanya bisa diakses melalui Telegram atau Jaringan VPN Kantor');
    });
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

  function doPingBypassAuth() {
    return fetch('/api/mini/auth/ping-bypass', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' }
    }).then(res => {
      if (!res.ok) throw new Error('Gagal melakukan bypass autentikasi');
      return res.json();
    }).then(data => {
      sessionStorage.setItem('miniapp_token', data.token);
      sessionStorage.setItem('miniapp_user', JSON.stringify(data.user));
      return data;
    });
  }

  return { start };
})();
