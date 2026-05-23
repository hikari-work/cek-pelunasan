package whatsapp

import (
	"context"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"go.mau.fi/whatsmeow/proto/waE2E"
	"go.mau.fi/whatsmeow/types"
	"go.mau.fi/whatsmeow/types/events"
	"google.golang.org/protobuf/proto"
)

// stubHandler menghitung berapa kali Match dan Handle dipanggil — alat untuk
// memverifikasi router benar-benar dispatch ke handler pertama yang match
// dan tidak ke yang lain.
type stubHandler struct {
	prefix      string
	matchCount  int32
	handleCount int32
	done        chan struct{}
}

func newStub(prefix string) *stubHandler {
	return &stubHandler{prefix: prefix, done: make(chan struct{}, 1)}
}

func (s *stubHandler) Match(m *IncomingMessage) bool {
	atomic.AddInt32(&s.matchCount, 1)
	return m != nil && len(m.Body) >= len(s.prefix) && m.Body[:len(s.prefix)] == s.prefix
}

func (s *stubHandler) Handle(_ context.Context, _ *IncomingMessage) {
	atomic.AddInt32(&s.handleCount, 1)
	select {
	case s.done <- struct{}{}:
	default:
	}
}

func makeIncoming(body, sender string, isGroup bool) *IncomingMessage {
	return &IncomingMessage{
		Info: types.MessageInfo{
			MessageSource: types.MessageSource{
				Chat:    types.JID{User: sender, Server: "s.whatsapp.net"},
				Sender:  types.JID{User: sender, Server: "s.whatsapp.net"},
				IsGroup: isGroup,
			},
			ID: "MID",
		},
		Body:    body,
		IsGroup: isGroup,
	}
}

// waitForHandler menunggu handler dipanggil sampai timeout. Karena dispatch
// pakai goroutine, test tidak bisa langsung baca counter tanpa sinkronisasi.
func waitForHandler(t *testing.T, s *stubHandler) {
	t.Helper()
	select {
	case <-s.done:
	case <-time.After(500 * time.Millisecond):
		t.Fatal("handler tidak dipanggil dalam 500ms")
	}
}

func TestRouter_DispatchExclusive(t *testing.T) {
	r := NewRouter("")
	first := newStub(".p")
	second := newStub(".p")
	r.Add(first)
	r.Add(second)

	r.dispatch(makeIncoming(".p 010600001234", "62811", false))
	waitForHandler(t, first)

	if atomic.LoadInt32(&first.handleCount) != 1 {
		t.Errorf("first handler should be called once, got %d", first.handleCount)
	}
	if atomic.LoadInt32(&second.handleCount) != 0 {
		t.Errorf("second handler should NOT be called, got %d", second.handleCount)
	}
}

func TestRouter_NoMatchSilent(t *testing.T) {
	r := NewRouter("")
	r.Add(newStub(".p"))

	// Tidak boleh panic, tidak boleh deadlock.
	r.dispatch(makeIncoming("halo apa kabar", "62811", false))

	// Beri waktu kalau ternyata ada goroutine — counter harus tetap 0.
	time.Sleep(50 * time.Millisecond)
}

func TestRouter_PanicRecovered(t *testing.T) {
	r := NewRouter("")
	panicker := &panicHandler{done: make(chan struct{}, 1)}
	r.Add(panicker)

	r.dispatch(makeIncoming("halo", "62811", false))

	select {
	case <-panicker.done:
	case <-time.After(500 * time.Millisecond):
		t.Fatal("panic handler tidak dipanggil")
	}
	// Test berhasil kalau test process tidak crash — recover bekerja.
}

type panicHandler struct{ done chan struct{} }

func (p *panicHandler) Match(_ *IncomingMessage) bool { return true }
func (p *panicHandler) Handle(_ context.Context, _ *IncomingMessage) {
	defer func() { p.done <- struct{}{} }()
	panic("boom")
}

func TestRouter_AdminMatchers(t *testing.T) {
	r := NewRouter("62811234567")

	cases := []struct {
		name        string
		sender      string
		isGroup     bool
		wantAdmin   bool
		wantInGroup bool
	}{
		{"admin DM", "62811234567", false, true, false},
		{"admin in group", "62811234567", true, true, true},
		{"admin substring (multi-device)", "62811234567:99", false, true, false},
		{"non-admin", "6285999", false, false, false},
		{"non-admin in group", "6285999", true, false, false},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			m := makeIncoming("/coba", c.sender, c.isGroup)
			if got := r.IsFromAdmin(m); got != c.wantAdmin {
				t.Errorf("IsFromAdmin = %v, want %v", got, c.wantAdmin)
			}
			if got := r.IsAdminInGroup(m); got != c.wantInGroup {
				t.Errorf("IsAdminInGroup = %v, want %v", got, c.wantInGroup)
			}
		})
	}
}

func TestRouter_AdminMatcherNilSafe(t *testing.T) {
	r := NewRouter("62811")
	if r.IsFromAdmin(nil) {
		t.Error("nil message should not match admin")
	}
	if r.IsAdminInGroup(nil) {
		t.Error("nil message should not match admin in group")
	}

	rEmpty := NewRouter("")
	m := makeIncoming("/x", "62811", false)
	if rEmpty.IsFromAdmin(m) {
		t.Error("empty admin config should never match")
	}
}

func TestRouter_EventHandlerSkipsFromMe(t *testing.T) {
	r := NewRouter("")
	stub := newStub(".p")
	r.Add(stub)
	r.rootCtx = context.Background()

	evt := &events.Message{
		Info: types.MessageInfo{
			MessageSource: types.MessageSource{
				Chat:     types.JID{User: "62811", Server: "s.whatsapp.net"},
				Sender:   types.JID{User: "62811", Server: "s.whatsapp.net"},
				IsFromMe: true,
			},
			ID: "X",
		},
		Message: &waE2E.Message{
			Conversation: proto.String(".p 010600001234"),
		},
	}
	r.eventHandler(evt)

	time.Sleep(50 * time.Millisecond)
	if atomic.LoadInt32(&stub.handleCount) != 0 {
		t.Errorf("IsFromMe message should not dispatch, got %d calls", stub.handleCount)
	}
}

func TestRouter_AddNilNoop(t *testing.T) {
	r := NewRouter("")
	r.Add(nil)
	// dispatch jangan panic walaupun handlers kosong.
	r.dispatch(makeIncoming("halo", "62811", false))
}

func TestRouter_ConcurrentAdd(t *testing.T) {
	r := NewRouter("")
	var wg sync.WaitGroup
	for i := 0; i < 50; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			r.Add(newStub(".p"))
		}()
	}
	wg.Wait()
	r.mu.RLock()
	got := len(r.handlers)
	r.mu.RUnlock()
	if got != 50 {
		t.Errorf("handlers = %d, want 50", got)
	}
}
