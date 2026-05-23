package whahandler

import (
	"testing"

	"github.com/hikari-work/cek-pelunasan/internal/platform/whatsapp"
	"github.com/hikari-work/cek-pelunasan/internal/service/email"
)

func TestEmail_Match(t *testing.T) {
	router := whatsapp.NewRouter("62811234567")
	h := &Email{Router: router}

	cases := []struct {
		name string
		body string
		from string
		want bool
	}{
		{"admin .email", ".email", "62811234567", true},
		{"admin .email arg", ".email user@example.com", "62811234567", true},
		{"admin .done", ".done", "62811234567", true},
		{"admin .email leading space", "  .email  ", "62811234567", true},
		{"non-admin .email", ".email", "6285999", false},
		{"non-admin .done", ".done", "6285999", false},
		{"admin .emailx", ".emailx", "62811234567", false},
		{"admin .donex", ".donex", "62811234567", false},
		{"admin lainnya", ".p 010600001234", "62811234567", false},
		{"empty", "", "62811234567", false},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			if got := h.Match(makeMsg(c.body, c.from)); got != c.want {
				t.Errorf("Match(%q from %q) = %v, want %v", c.body, c.from, got, c.want)
			}
		})
	}
}

func TestEmail_MatchNilRouter(t *testing.T) {
	h := &Email{Router: nil}
	if h.Match(makeMsg(".email", "62811")) {
		t.Error("nil router should not match")
	}
}

func TestEmail_ResolveRecipient(t *testing.T) {
	h := &Email{DefaultRecipient: "default@example.com"}
	cases := map[string]string{
		".email":                      "default@example.com",
		".email user@example.com":     "user@example.com",
		".email   spaced@example.com": "spaced@example.com",
		".email not-an-email":         "",
		".email user@invalid":         "", // tld minimal 2 char
		".email @example.com":         "",
	}
	for in, want := range cases {
		t.Run(in, func(t *testing.T) {
			got := h.resolveRecipient(in)
			if got != want {
				t.Errorf("resolveRecipient(%q) = %q, want %q", in, got, want)
			}
		})
	}
}

func TestEmail_ResolveRecipient_NoDefault(t *testing.T) {
	h := &Email{DefaultRecipient: ""}
	if got := h.resolveRecipient(".email"); got != "" {
		t.Errorf("no default + .email = %q, want empty", got)
	}
}

func TestEmailCollector_Match(t *testing.T) {
	cache := email.NewSessionCache()
	h := &EmailCollector{Sessions: cache}

	noMedia := makeMsg("hi", "62811")
	if h.Match(noMedia) {
		t.Error("non-media message should not match")
	}

	withMedia := makeMsg("caption", "62811")
	withMedia.MediaKind = "image"
	if h.Match(withMedia) {
		t.Error("media without active session should not match")
	}

	cache.Put(&email.Session{SenderPhone: "62811"}, nil)
	defer cache.Remove("62811")
	if !h.Match(withMedia) {
		t.Error("media with active session should match")
	}

	other := makeMsg("caption", "6285999")
	other.MediaKind = "image"
	if h.Match(other) {
		t.Error("session for different sender should not trigger match")
	}
}

func TestEmailCollector_MatchNilCache(t *testing.T) {
	h := &EmailCollector{Sessions: nil}
	m := makeMsg("x", "62811")
	m.MediaKind = "image"
	if h.Match(m) {
		t.Error("nil cache should not match")
	}
}

func TestDefaultMediaFilename(t *testing.T) {
	cases := []struct {
		kind  string
		index int
		want  string
	}{
		{"image", 1, "image_1.jpg"},
		{"video", 2, "video_2.mp4"},
		{"audio", 3, "audio_3.ogg"},
		{"sticker", 4, "sticker_4.webp"},
		{"document", 5, "document_5.bin"},
		{"", 6, "file_6.bin"},
	}
	for _, c := range cases {
		got := defaultMediaFilename(c.kind, c.index)
		if got != c.want {
			t.Errorf("defaultMediaFilename(%q, %d) = %q, want %q", c.kind, c.index, got, c.want)
		}
	}
}

func TestSanitizeFilename(t *testing.T) {
	cases := map[string]string{
		"normal.pdf":        "normal.pdf",
		"path/with/dir.pdf": "dir.pdf",
		"../../etc/passwd":  "passwd",
		"":                  "attachment",
		".":                 "attachment",
		"/":                 "attachment",
	}
	for in, want := range cases {
		if got := sanitizeFilename(in); got != want {
			t.Errorf("sanitizeFilename(%q) = %q, want %q", in, got, want)
		}
	}
}

func TestResolveMediaFilename_NilMessage(t *testing.T) {
	got := resolveMediaFilename(nil, "image", 1)
	if got != "image_1.jpg" {
		t.Errorf("nil msg fallback = %q, want image_1.jpg", got)
	}
}
