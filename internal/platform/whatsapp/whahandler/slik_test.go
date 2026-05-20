package whahandler

import (
	"testing"

	"github.com/hikari-work/cek-pelunasan/internal/platform/whatsapp"
)

func TestSlik_Match(t *testing.T) {
	h := &Slik{}
	cases := []struct {
		body string
		want bool
	}{
		{".slik budi", true},
		{".slik ", true},
		{".slik", false},
		{"slik budi", false},
		{".sliky budi", false},
		{".p 010600001234", false},
		{"", false},
	}
	for _, c := range cases {
		if got := h.Match(makeMsg(c.body, "62811")); got != c.want {
			t.Errorf("Match(%q) = %v, want %v", c.body, got, c.want)
		}
	}
}

func TestSlik_MatchNilMessage(t *testing.T) {
	h := &Slik{}
	if h.Match(nil) {
		t.Error("nil message should not match")
	}
}

func TestMatchSlikFile(t *testing.T) {
	keys := []string{
		"2026_05/pdf/KTP_3201234567890123.txt",
		"2026_05/pdf/SMG_2024_budi_santoso.pdf",
		"2026_05/pdf/JKT_2024_dewi_lestari.pdf",
		"2026_05/pdf/KTP_anything.pdf", // file di subfolder pdf tapi diawali KTP_
	}
	cases := []struct {
		name  string
		query string
		want  string
	}{
		{"hit budi", "budi", "2026_05/pdf/SMG_2024_budi_santoso.pdf"},
		{"case insensitive", "BUDI", "2026_05/pdf/SMG_2024_budi_santoso.pdf"},
		{"partial dewi", "dewi", "2026_05/pdf/JKT_2024_dewi_lestari.pdf"},
		{"no match", "andi", ""},
		{"skip KTP_ prefix", "anything", ""},
		{"empty query matches first non-KTP", "", "2026_05/pdf/SMG_2024_budi_santoso.pdf"},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			if got := matchSlikFile(c.query, keys); got != c.want {
				t.Errorf("matchSlikFile(%q) = %q, want %q", c.query, got, c.want)
			}
		})
	}
}

func TestFolderFromPDFKey(t *testing.T) {
	cases := map[string]string{
		"2026_05/pdf/SMG_budi.pdf":     "2026_05",
		"2024_12/pdf/sub/file.pdf":     "2024_12",
		"no_pdf_segment.pdf":           "",
		"":                             "",
		"/pdf/leading.pdf":             "",
		"folder/notpdf/SMG_budi.pdf":   "",
	}
	for in, want := range cases {
		if got := folderFromPDFKey(in); got != want {
			t.Errorf("folderFromPDFKey(%q) = %q, want %q", in, got, want)
		}
	}
}

func TestExtractSlikDisplayName(t *testing.T) {
	cases := map[string]string{
		"2026_05/pdf/SMG_2024_budi_santoso.pdf":     "Budi_Santoso",
		"SMG_2024_budi.pdf":                         "Budi",
		"SMG_2024_budi_santoso_pratama.pdf":         "Budi_Santoso_Pratama",
		"only.pdf":                                  "only",
		"two_segments.pdf":                          "two_segments",
		"SMG_2024__santoso.pdf":                     "Santoso", // segmen kosong di-skip
		"2026_05/pdf/SMG_2024_aLi.pdf":              "ALi",     // hanya huruf pertama yang di-uppercase
	}
	for in, want := range cases {
		t.Run(in, func(t *testing.T) {
			if got := extractSlikDisplayName(in); got != want {
				t.Errorf("extractSlikDisplayName(%q) = %q, want %q", in, got, want)
			}
		})
	}
}

func TestSlik_HandleEmptyQuery(t *testing.T) {
	// Sender nil → handler return diam-diam (guard di Handle).
	// Untuk empty query path, butuh sender mock; cukup pastikan tidak panic.
	h := &Slik{}
	// Sender nil → return langsung tanpa cek query. Tidak panic.
	h.Handle(nil, makeMsg(".slik ", "62811")) //nolint:staticcheck // ctx nil sengaja di guard sender
}

// Sanity check FolderProvider override dipakai saat Storage nil.
func TestSlik_FolderProvider(t *testing.T) {
	called := false
	h := &Slik{
		FolderProvider: func() string {
			called = true
			return "9999_99"
		},
	}
	got := h.currentFolder()
	if !called {
		t.Error("FolderProvider tidak dipanggil")
	}
	if got != "9999_99" {
		t.Errorf("currentFolder = %q, want %q", got, "9999_99")
	}
}

var _ = whatsapp.IncomingMessage{} // ensure import retained
