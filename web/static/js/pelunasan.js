/**
 * pelunasan.js — Render kartu dan detail untuk layanan Pelunasan.
 * Total = Baki Debet + Perhitungan Bunga + Penalty + Denda
 */

const Pelunasan = (() => {

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
          <span class="result-meta-item">${escHtml(item.branch || '-')}</span>
          <span class="result-meta-item">${escHtml(item.product || '-')}</span>
          ${item.dayLate && item.dayLate !== '0' ? `<span class="result-meta-item">${escHtml(item.dayLate)} HARI</span>` : ''}
        </div>
        <div class="result-amount-row">
          <span class="result-amount-label">Total Lunas</span>
          <span class="result-amount">${formatRupiah(item.fullPayment)}</span>
        </div>
      </div>
    `;
    div.addEventListener('click', () => App.openDetail('pelunasan', item.noSpk));
    return div;
  }

  function renderDetail(data, container) {
    container.innerHTML = `
      <div class="detail-hero is-pelunasan">
        <div class="detail-hero-accent"></div>
        <div class="detail-hero-body">
          <div class="detail-hero-name">${escHtml(data.nama || '-')}</div>
          <div class="detail-hero-meta">
            <span class="detail-hero-meta-item">${escHtml(data.spk || '-')}</span>
            ${data.alamat ? `<span class="detail-hero-meta-item">${escHtml(data.alamat)}</span>` : ''}
          </div>
        </div>
      </div>

      <div class="detail-section">
        <div class="detail-section-title">Tanggal Penting</div>
        <div class="detail-rows">
          ${row('Tgl Realisasi', data.tglRealisasi || '-')}
          ${row('Tgl Jatuh Tempo', data.tglJatuhTempo || '-')}
          ${row('Rencana Pelunasan', data.rencanaPelunasan || '-')}
        </div>
      </div>

      <div class="detail-section">
        <div class="detail-section-title">Rincian Kredit</div>
        <div class="detail-rows">
          ${row('Plafond', formatRupiah(data.plafond), 'mono')}
          ${row('Baki Debet', formatRupiah(data.bakiDebet), 'mono')}
        </div>
      </div>

      <div class="detail-section">
        <div class="detail-section-title">Komponen Pelunasan</div>
        <div class="detail-rows">
          ${row(data.typeBunga || 'Perhitungan Bunga', formatRupiah(data.perhitunganBunga), 'mono')}
          ${row(`Penalty (${data.multiplierPenalty ?? 0}×)`, formatRupiah(data.penalty), 'mono')}
          ${row('Denda', formatRupiah(data.denda), 'mono')}
        </div>
      </div>

      <div class="detail-total-card">
        <span class="detail-total-label">TOTAL PELUNASAN</span>
        <span class="detail-total-value">${formatRupiah(data.totalPelunasan)}</span>
      </div>
    `;
  }

  return { buildCard, renderDetail };
})();
