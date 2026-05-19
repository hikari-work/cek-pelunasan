package whahandler

import (
	"context"
	"testing"
	"time"

	"github.com/hikari-work/cek-pelunasan/internal/platform/whatsapp"
	"go.mau.fi/whatsmeow/types"
)

func makeMsg(body, sender string) *whatsapp.IncomingMessage {
	return &whatsapp.IncomingMessage{
		Info: types.MessageInfo{
			MessageSource: types.MessageSource{
				Chat:   types.JID{User: sender, Server: "s.whatsapp.net"},
				Sender: types.JID{User: sender, Server: "s.whatsapp.net"},
			},
			ID: "MSG-1",
		},
		Body: body,
	}
}

func TestShortcut_Match(t *testing.T) {
	router := whatsapp.NewRouter("62811234567")
	h := &Shortcut{Router: router}

	cases := []struct {
		name string
		body string
		from string
		want bool
	}{
		{"admin /coba", "/coba", "62811234567", true},
		{"admin /tunggu", "/tunggu", "62811234567", true},
		{"admin unknown /xyz", "/xyz", "62811234567", true}, // match prefix; Handle log warn
		{"non-admin /coba", "/coba", "6285999", false},
		{"admin tanpa slash", "halo", "62811234567", false},
		{"admin slash kosong", "/", "62811234567", true},
	}

	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			if got := h.Match(makeMsg(c.body, c.from)); got != c.want {
				t.Errorf("Match(%q from %q) = %v, want %v", c.body, c.from, got, c.want)
			}
		})
	}
}

func TestShortcut_MatchNilSafe(t *testing.T) {
	h := &Shortcut{Router: whatsapp.NewRouter("62811")}
	if h.Match(nil) {
		t.Error("nil message should not match")
	}
	h2 := &Shortcut{Router: nil}
	if h2.Match(makeMsg("/coba", "62811")) {
		t.Error("nil router should not match")
	}
}

// TestShortcut_PresetTable verifikasi semua entry preset terdaftar dan
// non-empty — kalau ada typo saat refactor, test ini gagal.
func TestShortcut_PresetTable(t *testing.T) {
	wantKeys := []string{"/coba", "/kasih", "/tunggu", "/relog", "/selesai", "/enter", "/input", "/display", "/terima"}
	if len(shortcutPresets) != len(wantKeys) {
		t.Fatalf("preset count = %d, want %d", len(shortcutPresets), len(wantKeys))
	}
	for _, k := range wantKeys {
		v, ok := shortcutPresets[k]
		if !ok {
			t.Errorf("preset %q missing", k)
		}
		if v == "" {
			t.Errorf("preset %q has empty body", k)
		}
	}
}

// TestShortcut_HandleNilSenderSafe pastikan Handle dengan sender nil
// tidak panic — handler hanya return.
func TestShortcut_HandleNilSenderSafe(t *testing.T) {
	h := &Shortcut{Sender: nil, Router: whatsapp.NewRouter("62811")}
	done := make(chan struct{})
	go func() {
		defer close(done)
		h.Handle(context.Background(), makeMsg("/coba", "62811"))
	}()
	select {
	case <-done:
	case <-time.After(200 * time.Millisecond):
		t.Fatal("Handle hang dengan nil sender")
	}
}

// TestShortcut_HandleUnknownCommandNoCrash: cmd tidak terdaftar dilewatkan
// tanpa balas apa pun — log warn, handler return.
func TestShortcut_HandleUnknownCommandNoCrash(t *testing.T) {
	h := &Shortcut{Sender: nil, Router: whatsapp.NewRouter("62811")}
	done := make(chan struct{})
	go func() {
		defer close(done)
		h.Handle(context.Background(), makeMsg("/foo", "62811"))
	}()
	select {
	case <-done:
	case <-time.After(200 * time.Millisecond):
		t.Fatal("Handle hang dengan unknown command")
	}
}
