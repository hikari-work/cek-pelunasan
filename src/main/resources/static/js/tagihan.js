/**
 * tagihan.js — Render kartu dan detail untuk layanan Tagihan.
 */

const Tagihan = (() => {

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
          <span class="result-amount-label">Angsuran</span>
          <span class="result-amount">${formatRupiah(item.installment)}</span>
        </div>
      </div>
    `;
    div.addEventListener('click', () => App.openDetail('tagihan', item.noSpk));
    return div;
  }

  function renderDetail(data, container) {
    const dayLate = parseInt(data.dayLate) || 0;
    container.innerHTML = `
      <div class="detail-hero is-tagihan">
        <div class="detail-hero-accent"></div>
        <div class="detail-hero-body">
          <div class="detail-hero-name">${escHtml(data.name || '-')}</div>
          <div class="detail-hero-meta">
            <span class="detail-hero-meta-item">${escHtml(data.noSpk || '-')}</span>
            <span class="detail-hero-meta-item">${escHtml(data.branch || '-')}</span>
            <span class="detail-hero-meta-item">${escHtml(data.product || '-')}</span>
          </div>
        </div>
      </div>

      <div class="detail-section">
        <div class="detail-section-title">Status Kredit</div>
        <div class="detail-rows">
          ${row('Kolektibilitas', kolLabel(data.collectStatus))}
          ${row('Hari Terlambat', dayLate > 0 ? dayLate + ' hari' : '0 hari', dayLate > 90 ? 'alert' : dayLate > 0 ? '' : 'good')}
          ${row('Jatuh Tempo', data.dueDate || '-')}
          ${row('Realisasi', data.realization || '-')}
        </div>
      </div>

      <div class="detail-section">
        <div class="detail-section-title">Rincian Tagihan</div>
        <div class="detail-rows">
          ${row('Plafond', formatRupiah(data.plafond), 'mono')}
          ${row('Sisa Pokok (Debet)', formatRupiah(data.debitTray), 'mono')}
          ${row('Angsuran / Bulan', formatRupiah(data.installment), 'big')}
          ${row('Bunga', formatRupiah(data.interest), 'mono')}
          ${row('Pokok', formatRupiah(data.principal), 'mono')}
        </div>
      </div>

      ${(data.penaltyInterest > 0 || data.penaltyPrincipal > 0) ? `
      <div class="detail-section">
        <div class="detail-section-title">Tunggakan & Denda</div>
        <div class="detail-rows">
          ${data.penaltyInterest > 0 ? row('Denda Bunga', formatRupiah(data.penaltyInterest), 'mono alert') : ''}
          ${data.penaltyPrincipal > 0 ? row('Denda Pokok', formatRupiah(data.penaltyPrincipal), 'mono alert') : ''}
        </div>
      </div>` : ''}

      <div class="detail-section">
        <div class="detail-section-title">Pembayaran Minimum</div>
        <div class="detail-rows">
          ${row('Min. Bunga', formatRupiah(data.minInterest), 'mono')}
          ${row('Min. Pokok', formatRupiah(data.minPrincipal), 'mono')}
          ${row('Titipan', formatRupiah(data.titipan), 'mono')}
        </div>
      </div>

      <div class="detail-section">
        <div class="detail-section-title">Account Officer</div>
        <div class="detail-rows">
          ${row('AO', data.accountOfficer || '-')}
          ${data.kios ? row('Kios', data.kios) : ''}
          ${data.address ? row('Alamat', data.address) : ''}
        </div>
      </div>
    `;
  }

  return { buildCard, renderDetail };
})();

/* =====================================================
   SHARED UTILITIES (dipakai semua modul)
   ===================================================== */

function formatRupiah(val) {
  if (val == null || val === 0) return 'Rp 0';
  return new Intl.NumberFormat('id-ID', {
    style: 'currency', currency: 'IDR', maximumFractionDigits: 0
  }).format(val);
}

function escHtml(str) {
  return String(str)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;')
    .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function kolBadge(status) {
  const cls = status ? `kol-${status}` : '';
  const label = status ? `KOL ${status}` : 'KOL -';
  return `<span class="result-badge ${cls}">${label}</span>`;
}

function kolLabel(status) {
  const labels = { '1': 'Lancar', '2': 'DPK', '3': 'Kurang Lancar', '4': 'Diragukan', '5': 'Macet' };
  return status ? `${status} — ${labels[status] || status}` : '-';
}

function row(label, value, valueCls = '') {
  return `
    <div class="detail-row${valueCls === 'big' ? ' highlight-row' : ''}">
      <span class="detail-row-label">${label}</span>
      <span class="detail-row-value ${valueCls}">${value}</span>
    </div>`;
}
