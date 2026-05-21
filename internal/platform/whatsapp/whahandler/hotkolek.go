package whahandler

import (
	"context"
	"log/slog"
	"regexp"
	"strings"
	"time"

	"github.com/hikari-work/cek-pelunasan/internal/platform/whatsapp"
	"github.com/hikari-work/cek-pelunasan/internal/service/hotkolek"
)

// HotKolek menangani perintah hot koleksi: "{prefix}010600001234" atau beberapa
// SPK dipisah spasi ("{prefix}010600001234 010600005678").
//
// Flow legacy:
//
//  1. Validasi pola.
//  2. Reaction emoji ke pesan asli (delay 2s untuk meniru "loading").
//  3. Ekstrak nomor SPK (strip prefix di token pertama).
//  4. Simpan paying flag untuk semua SPK (tandai "sudah dibayar hari ini").
//  5. Generate rekap 3 lokasi (Kaligondang/Kalikajar/Kejobong) dengan 3
//     kategori per lokasi (Minimal Bayar/Angsuran Pertama/Jatuh Tempo).
//  6. Kirim rekap ke chat asal.
//
// HotKolek dijalankan untuk siapa saja yang ngirim pola ini — tidak ada
// admin gate. AO lapangan biasa pakai dari grup mereka.
type HotKolek struct {
	Service *hotkolek.Service
	Sender  *whatsapp.Sender
	Prefix  string // default "." kalau kosong
}

// hotKolekPattern: prefix command diikuti 12 digit, optional tambahan dipisah
// spasi. Pattern di-compile per-handler di Match() supaya prefix dinamis.
// Default prefix "." → ^\.\d{12}(?:\s\d{12})*$.
func (h *HotKolek) hotKolekPattern() *regexp.Regexp {
	p := h.Prefix
	if p == "" {
		p = defaultPrefix
	}
	return regexp.MustCompile(`^` + regexp.QuoteMeta(p) + `\d{12}(?:\s\d{12})*$`)
}

// kiosConfigs urut sama dengan legacy supaya output identik.
type kiosConfig struct {
	location string
	code     string
}

var kiosConfigs = []kiosConfig{
	{location: "Kaligondang", code: "1075"},
	{location: "Kalikajar", code: "KLJ"},
	{location: "Kejobong", code: "KJB"},
}

func (h *HotKolek) Match(m *whatsapp.IncomingMessage) bool {
	if m == nil {
		return false
	}
	return h.hotKolekPattern().MatchString(strings.TrimSpace(m.Body))
}

func (h *HotKolek) Handle(ctx context.Context, m *whatsapp.IncomingMessage) {
	if h.Sender == nil || h.Service == nil {
		return
	}

	body := strings.TrimSpace(m.Body)
	spks := extractSPKs(body, h.Prefix)
	if len(spks) == 0 {
		// Match() sudah validasi pola, tapi safety untuk extractor.
		return
	}

	// Reaction "sedang diproses" — kirim segera supaya user tahu pesan
	// sudah diterima. Goroutine supaya tidak nge-block flow utama.
	go func() {
		if err := h.Sender.React(ctx, m.Info); err != nil {
			slog.Warn("hotkolek: reaction gagal", "err", err)
		}
	}()

	if err := h.Service.SaveAllPaying(ctx, spks); err != nil {
		slog.Error("hotkolek: simpan paying gagal", "spks", spks, "err", err)
		// Jangan return — tetap kirim rekap supaya user dapat info terbaru.
	}

	locations, err := h.buildLocations(ctx)
	if err != nil {
		slog.Error("hotkolek: build rekap gagal", "err", err)
		_, _ = h.Sender.SendText(ctx, m.ChatJID(), "❌ Gagal mengambil rekap. Silakan coba lagi.", &m.Info)
		return
	}

	now := time.Now().In(time.FixedZone("WIB", 7*3600))
	msg := hotkolek.FormatHotKolekMessage(locations, now)
	if _, err := h.Sender.SendText(ctx, m.ChatJID(), msg, nil); err != nil {
		slog.Error("hotkolek: kirim rekap gagal", "err", err)
	}
}

// buildLocations panggil BuildLocations sekali (4 query paralel) lalu rakit
// struct LocationBills per kios. Order kategori per legacy: minimal pay
// (header kosong), "Angsuran Pertama", "Jatuh tempo".
func (h *HotKolek) buildLocations(ctx context.Context) ([]hotkolek.LocationBills, error) {
	codes := make([]string, 0, len(kiosConfigs))
	for _, c := range kiosConfigs {
		codes = append(codes, c.code)
	}
	byKios, err := h.Service.BuildLocations(ctx, codes)
	if err != nil {
		return nil, err
	}
	out := make([]hotkolek.LocationBills, 0, len(kiosConfigs))
	for _, c := range kiosConfigs {
		cat := byKios[c.code]
		out = append(out, hotkolek.LocationBills{
			Name: c.location,
			Category: []hotkolek.CategoryBills{
				{Header: "", Bills: cat.MinimalPay},
				{Header: "Angsuran Pertama", Bills: cat.FirstPay},
				{Header: "Jatuh tempo", Bills: cat.DueDate},
			},
		})
	}
	return out, nil
}

// extractSPKs split by whitespace, strip prefix kalau ada, ambil
// hanya yang 12 digit angka. prefix boleh kosong → fallback "."
// supaya tetap kompatibel dengan caller test yang tidak set prefix.
func extractSPKs(text, prefix string) []string {
	if prefix == "" {
		prefix = defaultPrefix
	}
	tokens := strings.Fields(text)
	out := make([]string, 0, len(tokens))
	for _, tok := range tokens {
		tok = strings.TrimPrefix(tok, prefix)
		if len(tok) != 12 {
			continue
		}
		if !isAllDigits(tok) {
			continue
		}
		out = append(out, tok)
	}
	return out
}

func isAllDigits(s string) bool {
	if s == "" {
		return false
	}
	for _, r := range s {
		if r < '0' || r > '9' {
			return false
		}
	}
	return true
}
