/**
 * kolektas.js — Render kartu dan detail untuk layanan KolekTas.
 */

const Kolektas = (() => {

  function buildCard(item) {
    const div = document.createElement('div');
    div.className = 'result-card';
    div.innerHTML = `
      <div class="result-card-inner">
        <div class="result-card-header">
          <span class="result-name">${escHtml(item.nama || '-')}</span>
        </div>
        <div class="result-meta">
          <span class="result-meta-item">${escHtml(item.rekening || '-')}</span>
          <span class="result-meta-item">${escHtml(item.kantor || '-')}</span>
        </div>
        ${item.accountOfficer ? `<div class="result-address">AO: ${escHtml(item.accountOfficer)}</div>` : ''}
        <div class="result-amount-row">
          <span class="result-amount-label">Nominal</span>
          <span class="result-amount bad">${escHtml(item.nominal || '-')}</span>
        </div>
      </div>
    `;
    div.addEventListener('click', () => App.openDetail('kolektas', item.id));
    return div;
  }

  function renderDetail(data, container) {
    container.innerHTML = `
      <div class="detail-hero is-kolektas">
        <div class="detail-hero-accent"></div>
        <div class="detail-hero-body">
          <div class="detail-hero-name">${escHtml(data.nama || '-')}</div>
          <div class="detail-hero-meta">
            <span class="detail-hero-meta-item">${escHtml(data.rekening || '-')}</span>
            <span class="detail-hero-meta-item">${escHtml(data.kelompok || '-')}</span>
          </div>
        </div>
      </div>

      <div class="detail-total-card">
        <span class="detail-total-label">NOMINAL TUNGGAKAN</span>
        <span class="detail-total-value bad">${escHtml(data.nominal || '-')}</span>
      </div>

      <div class="detail-section">
        <div class="detail-section-title">Data Nasabah</div>
        <div class="detail-rows">
          ${row('CIF', data.cif || '-')}
          ${row('Nama', data.nama || '-')}
          ${row('No. Rekening', data.rekening || '-')}
          ${data.alamat ? row('Alamat', data.alamat) : ''}
          ${data.noHp ? row('No. HP', data.noHp) : ''}
        </div>
      </div>

      <div class="detail-section">
        <div class="detail-section-title">Informasi Kelompok</div>
        <div class="detail-rows">
          ${row('Kelompok', data.kelompok || '-')}
          ${row('Kantor', data.kantor || '-')}
          ${data.accountOfficer ? row('Account Officer', data.accountOfficer) : ''}
        </div>
      </div>
    `;
  }

  return { buildCard, renderDetail };
})();
