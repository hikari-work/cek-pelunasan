/**
 * search.js — Mengelola input pencarian, debounce, fetch API, dan render hasil.
 */

const Search = (() => {
  let debounceTimer = null;
  let currentService = null;
  let allItems = [];
  let activeBranch = null;

  const input      = document.getElementById('search-input');
  const clearBtn   = document.getElementById('search-clear');
  const titleEl    = document.getElementById('search-title');
  const hintEl     = document.getElementById('search-hint');
  const serviceBar = document.getElementById('search-service-bar');
  const filterBar    = document.getElementById('filter-bar');
  const stateIdle    = document.getElementById('search-state-idle');
  const stateLoading = document.getElementById('search-state-loading');
  const stateEmpty   = document.getElementById('search-state-empty');
  const resultsList  = document.getElementById('search-results');

  const SERVICE_META = {
    tagihan:   { label: 'TAGIHAN',   hint: 'Nama nasabah <b>atau</b> nomor SPK', cls: 'is-tagihan',   color: 'var(--c-tagihan)' },
    pelunasan: { label: 'PELUNASAN', hint: 'Nama nasabah <b>atau</b> nomor SPK', cls: 'is-pelunasan',  color: 'var(--c-pelunasan)' },
    tabungan:  { label: 'TABUNGAN',  hint: 'Nama nasabah <b>atau</b> nomor rekening', cls: 'is-tabungan', color: 'var(--c-tabungan)' },
  };

  function reset(service) {
    currentService = service;
    input.value = '';
    resultsList.innerHTML = '';
    clearBtn.classList.add('hidden');

    const meta = SERVICE_META[service] || SERVICE_META.tagihan;
    titleEl.textContent  = meta.label;
    titleEl.className    = 'search-title ' + meta.cls;
    hintEl.innerHTML     = meta.hint;
    serviceBar.style.background = meta.color;

    setStateIdle();
    setTimeout(() => input.focus(), 250);
  }

  function setStateIdle() {
    stateIdle.classList.remove('hidden');
    stateLoading.classList.add('hidden');
    stateEmpty.classList.add('hidden');
    resultsList.classList.add('hidden');
    hideFilterBar();
  }

  function setStateLoading() {
    stateIdle.classList.add('hidden');
    stateLoading.classList.remove('hidden');
    stateEmpty.classList.add('hidden');
    resultsList.classList.add('hidden');
    hideFilterBar();
  }

  function setStateEmpty() {
    stateIdle.classList.add('hidden');
    stateLoading.classList.add('hidden');
    stateEmpty.classList.remove('hidden');
    resultsList.classList.add('hidden');
    hideFilterBar();
  }

  function setStateResults(items) {
    stateIdle.classList.add('hidden');
    stateLoading.classList.add('hidden');
    stateEmpty.classList.add('hidden');
    resultsList.classList.remove('hidden');

    allItems = items;
    activeBranch = null;

    if (currentService === 'tabungan') {
      renderFilterBar(items);
    } else {
      hideFilterBar();
    }

    renderCards(items);
  }

  function renderCards(items) {
    resultsList.innerHTML = '';
    items.forEach(item => {
      let card;
      if (currentService === 'tabungan')       card = Tabungan.buildCard(item);
      else if (currentService === 'pelunasan')  card = Pelunasan.buildCard(item);
      else                                       card = Tagihan.buildCard(item);
      resultsList.appendChild(card);
    });
  }

  function renderFilterBar(items) {
    const branches = [...new Set(items.map(i => i.branch).filter(Boolean))].sort();
    if (branches.length <= 1) { hideFilterBar(); return; }

    filterBar.innerHTML = '';

    const allChip = document.createElement('button');
    allChip.className = 'filter-chip active';
    allChip.textContent = 'SEMUA';
    allChip.addEventListener('click', () => applyFilter(null));
    filterBar.appendChild(allChip);

    branches.forEach(branch => {
      const chip = document.createElement('button');
      chip.className = 'filter-chip';
      chip.textContent = branch;
      chip.dataset.branch = branch;
      chip.addEventListener('click', () => applyFilter(branch));
      filterBar.appendChild(chip);
    });

    filterBar.classList.remove('hidden');
  }

  function hideFilterBar() {
    filterBar.classList.add('hidden');
    filterBar.innerHTML = '';
  }

  function applyFilter(branch) {
    activeBranch = branch;
    filterBar.querySelectorAll('.filter-chip').forEach(chip => {
      const chipBranch = chip.dataset.branch || null;
      chip.classList.toggle('active', chipBranch === branch);
    });
    const filtered = branch ? allItems.filter(i => i.branch === branch) : allItems;
    renderCards(filtered);
  }

  function doSearch(query) {
    if (!query || query.trim().length < 2) { setStateIdle(); return; }

    setStateLoading();
    const token = App.getToken();
    const url = `/api/mini/${currentService}/search?q=${encodeURIComponent(query.trim())}&page=0`;

    fetch(url, { headers: { 'X-Mini-Token': token } })
      .then(res => {
        if (res.status === 401) {
          sessionStorage.removeItem('miniapp_token');
          App.showError('Sesi berakhir — buka ulang aplikasi');
          return [];
        }
        if (!res.ok) throw new Error('Gagal mengambil data');
        return res.json();
      })
      .then(items => {
        if (!items || items.length === 0) setStateEmpty();
        else setStateResults(items);
      })
      .catch(err => {
        App.showError(err.message || 'Terjadi kesalahan');
        setStateEmpty();
      });
  }

  input.addEventListener('input', () => {
    const q = input.value.trim();
    clearBtn.classList.toggle('hidden', !q);
    clearTimeout(debounceTimer);
    if (!q) { setStateIdle(); return; }
    debounceTimer = setTimeout(() => doSearch(q), 400);
  });

  clearBtn.addEventListener('click', () => {
    input.value = '';
    clearBtn.classList.add('hidden');
    setStateIdle();
    input.focus();
  });

  return { reset };
})();
