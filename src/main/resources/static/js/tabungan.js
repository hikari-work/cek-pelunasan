/**
 * tabungan.js — Render kartu dan detail untuk layanan Tabungan.
 */

const Tabungan = (() => {

  function buildCard(item) {
    const div = document.createElement('div');
    div.className = 'result-card';
    div.innerHTML = `
      <div class="result-card-inner">
        <div class="result-card-header">
          <span class="result-name">${escHtml(item.name || '-')}</span>
          <span class="result-badge">${escHtml(item.type || '-')}</span>
        </div>
        <div class="result-meta">
          <span class="result-meta-item">${escHtml(item.branch || '-')}</span>
          <span class="result-meta-item">${escHtml(item.tabId || '-')}</span>
        </div>
        <div class="result-amount-row">
          <span class="result-amount-label">Saldo</span>
          <span class="result-amount good">${formatRupiah(item.balance)}</span>
        </div>
      </div>
    `;
    div.addEventListener('click', () => App.openDetail('tabungan', item.tabId));
    return div;
  }

  function renderDetail(data, container) {
    container.innerHTML = `
      <div class="detail-hero is-tabungan">
        <div class="detail-hero-accent"></div>
        <div class="detail-hero-body">
          <div class="detail-hero-name">${escHtml(data.name || '-')}</div>
          <div class="detail-hero-meta">
            <span class="detail-hero-meta-item">${escHtml(data.tabId || '-')}</span>
            <span class="detail-hero-meta-item">${escHtml(data.branch || '-')}</span>
            <span class="detail-hero-meta-item">${escHtml(data.type || '-')}</span>
          </div>
        </div>
      </div>

      <div class="detail-total-card">
        <span class="detail-total-label">SALDO EFEKTIF</span>
        <span class="detail-total-value">${formatRupiah(data.balance)}</span>
      </div>

      <div class="detail-section">
        <div class="detail-section-title">Informasi Rekening</div>
        <div class="detail-rows">
          ${row('Nomor Rekening', data.tabId || '-')}
          ${row('Jenis Produk', data.type || '-')}
          ${row('CIF', data.cif || '-')}
          ${row('Cabang', data.branch || '-')}
          ${data.accountOfficer ? row('Account Officer', data.accountOfficer) : ''}
        </div>
      </div>

      <div class="detail-section">
        <div class="detail-section-title">Rincian Saldo</div>
        <div class="detail-rows">
          ${row('Saldo Efektif', formatRupiah(data.balance), 'good')}
          ${row('Saldo Minimum', formatRupiah(data.minimumBalance), 'mono')}
          ${row('Saldo Diblokir', formatRupiah(data.blockingBalance), 'mono')}
          ${row('Transaksi Terakhir', formatRupiah(data.transaction), 'mono')}
        </div>
      </div>

      <div class="detail-section">
        <div class="detail-section-title">Data Nasabah</div>
        <div class="detail-rows">
          ${row('Nama', data.name || '-')}
          ${data.address ? row('Alamat', data.address) : ''}
          ${data.phone ? row('Telepon', data.phone) : ''}
        </div>
      </div>
    `;
  }

  return { buildCard, renderDetail };
})();
