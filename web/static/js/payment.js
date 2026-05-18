/**
 * payment.js — Render kartu dan detail untuk layanan Payment.
 * Detail menampilkan riwayat angsuran nasabah per SPK dari koleksi payment_details,
 * plus tabel snapshot tunggakan & minimal dari Bills.
 */

const Payment = (() => {

  function buildCard(item) {
    const div = document.createElement('div');
    div.className = 'result-card';
    div.innerHTML = `
      <div class="result-card-inner">
        <div class="result-card-header">
          <span class="result-name">${escHtml(item.name || '-')}</span>
          ${kolBadge(item.collectStatus)}
        </div>
        <div class="result-meta">
          <span class="result-meta-item">${escHtml(item.noSpk || '-')}</span>
          <span class="result-meta-item">${escHtml(item.branch || '-')}</span>
          <span class="result-meta-item">${escHtml(item.product || '-')}</span>
        </div>
        <div class="result-amount-row">
          <span class="result-amount-label">Angsuran</span>
          <span class="result-amount">${formatRupiah(item.installment)}</span>
        </div>
      </div>
    `;
    div.addEventListener('click', () => App.openDetail('payment', item.noSpk));
    return div;
  }

  function renderDetail(data, container) {
    const rows = Array.isArray(data.rows) ? data.rows : [];
    container.innerHTML = `
      <div class="detail-hero is-payment">
        <div class="detail-hero-accent"></div>
        <div class="detail-hero-body">
          <div class="detail-hero-name">${escHtml(data.name || '-')}</div>
          <div class="detail-hero-meta">
            <span class="detail-hero-meta-item">${escHtml(data.noSpk || '-')}</span>
            ${data.branch ? `<span class="detail-hero-meta-item">${escHtml(data.branch)}</span>` : ''}
            ${data.product ? `<span class="detail-hero-meta-item">${escHtml(data.product)}</span>` : ''}
          </div>
        </div>
      </div>

      <div class="detail-section">
        <div class="detail-section-title">Riwayat Angsuran</div>
        ${rows.length === 0
          ? `<div class="payment-empty">Tidak ada data angsuran untuk SPK ini.</div>`
          : `<div class="payment-table-wrap">
              <table class="payment-table">
                <thead>
                  <tr>
                    <th>No</th>
                    <th>Tanggal</th>
                    <th>Type</th>
                    <th class="num">Pokok</th>
                    <th class="num">Bunga</th>
                    <th class="num">Total</th>
                  </tr>
                </thead>
                <tbody>
                  ${rows.map(r => `
                    <tr class="${r.highlight ? 'is-alert' : ''}">
                      <td>${r.no}</td>
                      <td class="mono">${escHtml(formatTanggal(r.tanggal))}</td>
                      <td><span class="type-badge type-${escHtml((r.typePosting || '').toLowerCase())}">${escHtml(r.typePosting || '-')}</span></td>
                      <td class="num mono">${formatRupiah(r.pokok)}</td>
                      <td class="num mono">${formatRupiah(r.bunga)}</td>
                      <td class="num mono">${formatRupiah(r.total)}</td>
                    </tr>
                  `).join('')}
                </tbody>
              </table>
            </div>`
        }
      </div>

      <div class="detail-section">
        <div class="detail-section-title">Tagihan Aktif</div>
        <div class="payment-table-wrap">
          <table class="payment-table">
            <thead>
              <tr>
                <th>Item</th>
                <th class="num">Pokok</th>
                <th class="num">Bunga</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>Tunggakan</td>
                <td class="num mono">${formatRupiah(data.tunggakanPokok)}</td>
                <td class="num mono">${formatRupiah(data.tunggakanBunga)}</td>
              </tr>
              <tr>
                <td>Minimal</td>
                <td class="num mono">${formatRupiah(data.minimalPokok)}</td>
                <td class="num mono">${formatRupiah(data.minimalBunga)}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    `;
  }

  function formatTanggal(yyyymmdd) {
    if (!yyyymmdd || yyyymmdd.length !== 8) return yyyymmdd || '-';
    return `${yyyymmdd.substring(6, 8)}/${yyyymmdd.substring(4, 6)}/${yyyymmdd.substring(0, 4)}`;
  }

  return { buildCard, renderDetail };
})();
