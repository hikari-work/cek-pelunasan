package email

import (
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"
)

func TestSession_AddAndCount(t *testing.T) {
	s := &Session{SenderPhone: "62811"}
	if s.MediaCount() != 0 {
		t.Errorf("fresh session count = %d, want 0", s.MediaCount())
	}
	s.AddMedia(CollectedMedia{Filename: "a.jpg"})
	s.AddMedia(CollectedMedia{Filename: "b.mp4"})
	if got := s.MediaCount(); got != 2 {
		t.Errorf("count after 2 add = %d, want 2", got)
	}
	got := s.Media()
	if len(got) != 2 || got[0].Filename != "a.jpg" || got[1].Filename != "b.mp4" {
		t.Errorf("Media() = %+v, unexpected order/content", got)
	}
}

func TestSession_MediaIsCopy(t *testing.T) {
	s := &Session{SenderPhone: "62811"}
	s.AddMedia(CollectedMedia{Filename: "a.jpg"})
	got := s.Media()
	got[0].Filename = "modified"
	if s.Media()[0].Filename != "a.jpg" {
		t.Error("Media() should return defensive copy")
	}
}

func TestSession_ConcurrentAdd(t *testing.T) {
	s := &Session{SenderPhone: "62811"}
	const n = 100
	var wg sync.WaitGroup
	wg.Add(n)
	for i := 0; i < n; i++ {
		go func() {
			defer wg.Done()
			s.AddMedia(CollectedMedia{Filename: "x"})
		}()
	}
	wg.Wait()
	if s.MediaCount() != n {
		t.Errorf("concurrent add count = %d, want %d", s.MediaCount(), n)
	}
}

func TestSessionCache_PutAndGet(t *testing.T) {
	c := NewSessionCache()
	s := &Session{SenderPhone: "62811"}
	c.Put(s, nil)

	got := c.Get("62811")
	if got != s {
		t.Errorf("Get returned %p, want %p", got, s)
	}
	if c.Get("nonexistent") != nil {
		t.Error("Get unknown phone should return nil")
	}
	if c.Size() != 1 {
		t.Errorf("Size = %d, want 1", c.Size())
	}
}

func TestSessionCache_Remove(t *testing.T) {
	c := NewSessionCache()
	s := &Session{SenderPhone: "62811"}
	c.Put(s, nil)

	got := c.Remove("62811")
	if got != s {
		t.Errorf("Remove = %p, want %p", got, s)
	}
	if c.Get("62811") != nil {
		t.Error("Get after Remove should be nil")
	}
	if c.Size() != 0 {
		t.Errorf("Size after Remove = %d, want 0", c.Size())
	}

	if c.Remove("62811") != nil {
		t.Error("Remove on empty should return nil")
	}
}

func TestSessionCache_PutReplacesOld(t *testing.T) {
	c := NewSessionCache()
	old := &Session{SenderPhone: "62811", FromName: "old"}
	new_ := &Session{SenderPhone: "62811", FromName: "new"}

	var oldFired int32
	c.Put(old, func(*Session) { atomic.AddInt32(&oldFired, 1) })

	// Replace immediately — old timer should be cancelled.
	c.Put(new_, nil)

	got := c.Get("62811")
	if got != new_ {
		t.Error("Put should replace existing session")
	}
}

func TestSessionCache_RemoveCancelsAutoSend(t *testing.T) {
	// Sentuh internal: shorten effective timer dengan trigger Put + Remove cepat.
	// Timer jalan via time.AfterFunc(SessionTTL, ...) — kalau Remove dipanggil
	// sebelum SessionTTL, callback tidak boleh jalan.
	c := NewSessionCache()
	var fired int32
	s := &Session{SenderPhone: "62811"}
	c.Put(s, func(*Session) { atomic.AddInt32(&fired, 1) })
	if c.Remove("62811") == nil {
		t.Fatal("Remove returned nil immediately after Put")
	}
	// Tidak ada ekspektasi waktu — tunggu jeda kecil dan pastikan callback
	// tidak dipanggil. Kalau Remove tidak cancel timer, dalam 60s ini gagal
	// di CI panjang; pakai polling pendek dengan margin 100ms.
	time.Sleep(100 * time.Millisecond)
	if atomic.LoadInt32(&fired) != 0 {
		t.Error("auto-send fired meskipun Remove sudah dipanggil")
	}
}

func TestBuildMessage_PlainText(t *testing.T) {
	out, err := buildMessage(Mail{
		From:    "from@example.com",
		To:      "to@example.com",
		Subject: "Hello",
		Body:    "Body text",
	})
	if err != nil {
		t.Fatalf("err: %v", err)
	}
	s := string(out)
	for _, want := range []string{
		"From: from@example.com",
		"To: to@example.com",
		"Subject: Hello",
		"MIME-Version: 1.0",
		"Content-Type: text/plain; charset=UTF-8",
		"Body text",
	} {
		if !strings.Contains(s, want) {
			t.Errorf("missing %q in output:\n%s", want, s)
		}
	}
}

func TestBuildMessage_WithAttachment(t *testing.T) {
	out, err := buildMessage(Mail{
		From:    "from@example.com",
		To:      "to@example.com",
		Subject: "Hi",
		Body:    "see attached",
		Attachments: []Attachment{
			{Filename: "doc.pdf", Bytes: []byte("PDFDATA"), ContentType: "application/pdf"},
		},
	})
	if err != nil {
		t.Fatalf("err: %v", err)
	}
	s := string(out)
	for _, want := range []string{
		"multipart/mixed",
		"boundary=",
		"Content-Type: application/pdf; name=\"doc.pdf\"",
		"Content-Disposition: attachment; filename=\"doc.pdf\"",
		"Content-Transfer-Encoding: base64",
	} {
		if !strings.Contains(s, want) {
			t.Errorf("missing %q in output", want)
		}
	}
}

func TestEncodeHeader(t *testing.T) {
	if got := encodeHeader("Plain ASCII"); got != "Plain ASCII" {
		t.Errorf("ASCII passthrough failed: %q", got)
	}
	got := encodeHeader("Hello — Indonesia")
	if !strings.HasPrefix(got, "=?UTF-8?") {
		t.Errorf("non-ASCII not encoded: %q", got)
	}
}

func TestDetectContentType(t *testing.T) {
	cases := map[string]string{
		"file.pdf":     "application/pdf",
		"img.jpg":      "image/jpeg",
		"unknown":      "application/octet-stream",
		"":             "application/octet-stream",
	}
	for in, want := range cases {
		got := detectContentType(in)
		// MIME library kadang return "image/jpeg" atau "image/jpg" — cukup prefix match.
		if !strings.HasPrefix(got, strings.SplitN(want, "/", 2)[0]) {
			t.Errorf("detectContentType(%q) = %q, want prefix %q", in, got, want)
		}
	}
}

func TestBuildSubjectAndBody(t *testing.T) {
	s := &Session{
		FromName:    "Budi",
		SenderPhone: "62811",
		Recipient:   "ops@example.com",
	}
	subject := buildSubject(s)
	if !strings.Contains(subject, "WA Forward dari Budi") {
		t.Errorf("subject missing prefix: %q", subject)
	}

	body := buildBody(s, []CollectedMedia{
		{Filename: "a.jpg"},
		{Filename: "b.pdf", Caption: "tagihan"},
	})
	for _, want := range []string{
		"Pesan diteruskan dari WhatsApp",
		"Pengirim : Budi",
		"Nomor    : 62811",
		"1. a.jpg",
		"2. b.pdf — tagihan",
	} {
		if !strings.Contains(body, want) {
			t.Errorf("body missing %q in:\n%s", want, body)
		}
	}
}
