// Package email berisi forwarder media WhatsApp ke email.
//
// Flow:
//
//	1. User kirim ".email" → buka session, TTL 60 detik.
//	2. Setiap media yang masuk saat session aktif → ditambah ke daftar.
//	3. User ketik ".done" atau TTL habis → kirim email via SMTP.
//
// Session disimpan in-memory, key = nomor pengirim WhatsApp (clean, tanpa
// suffix server). Aman dipakai concurrent.
package email

import (
	"sync"
	"time"
)

// SessionTTL durasi sesi sebelum auto-send dijalankan.
const SessionTTL = 60 * time.Second

// CollectedMedia satu file media dalam sesi siap-kirim.
//
// Bytes sudah di-download saat ditambahkan supaya saat kirim email
// tidak perlu re-download (whatsmeow memerlukan client + key untuk
// download, dan key bisa expire setelah beberapa menit).
type CollectedMedia struct {
	Filename  string
	Bytes     []byte
	MediaType string // "image" | "video" | "audio" | "document" | "sticker"
	Caption   string
}

// Session state pengirim selama mengumpulkan media.
type Session struct {
	ChatID      string
	SenderPhone string
	FromName    string
	Recipient   string

	mu    sync.Mutex
	media []CollectedMedia
}

// AddMedia tambahkan satu file ke sesi. Aman dipanggil concurrent.
func (s *Session) AddMedia(m CollectedMedia) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.media = append(s.media, m)
}

// Media salinan defensif daftar media. Caller boleh modify slice tanpa
// memengaruhi state internal.
func (s *Session) Media() []CollectedMedia {
	s.mu.Lock()
	defer s.mu.Unlock()
	out := make([]CollectedMedia, len(s.media))
	copy(out, s.media)
	return out
}

// MediaCount jumlah media tanpa salinan. Hemat alokasi untuk logging.
func (s *Session) MediaCount() int {
	s.mu.Lock()
	defer s.mu.Unlock()
	return len(s.media)
}

// AutoSendFn callback dipanggil saat TTL habis.
type AutoSendFn func(*Session)

// SessionCache map senderPhone → Session. Aman dipakai concurrent.
//
// TTL diatur lewat time.AfterFunc per sesi. Ketika user ketik .done lebih
// awal, timer di-Stop dan sesi dihapus tanpa memicu auto-send.
type SessionCache struct {
	mu       sync.Mutex
	sessions map[string]*sessionEntry
}

type sessionEntry struct {
	session *Session
	timer   *time.Timer
}

func NewSessionCache() *SessionCache {
	return &SessionCache{sessions: make(map[string]*sessionEntry)}
}

// Put simpan sesi baru. Sesi lama untuk pengirim yang sama akan di-cancel
// (timer Stop) sebelum diganti — tidak ada double-send.
//
// onAutoSend jalan di goroutine baru saat TTL habis.
func (c *SessionCache) Put(s *Session, onAutoSend AutoSendFn) {
	c.mu.Lock()
	defer c.mu.Unlock()

	phone := s.SenderPhone
	if existing, ok := c.sessions[phone]; ok {
		existing.timer.Stop()
	}

	entry := &sessionEntry{session: s}
	entry.timer = time.AfterFunc(SessionTTL, func() {
		c.mu.Lock()
		current, ok := c.sessions[phone]
		if !ok || current.session != s {
			// Sesi sudah diganti / dihapus — biarkan.
			c.mu.Unlock()
			return
		}
		delete(c.sessions, phone)
		c.mu.Unlock()
		if onAutoSend != nil {
			go onAutoSend(s)
		}
	})
	c.sessions[phone] = entry
}

// Get ambil sesi aktif. Tidak menghapus, TTL tetap jalan.
func (c *SessionCache) Get(phone string) *Session {
	c.mu.Lock()
	defer c.mu.Unlock()
	if entry, ok := c.sessions[phone]; ok {
		return entry.session
	}
	return nil
}

// Remove ambil sesi sambil cancel TTL. Auto-send tidak akan jalan untuk
// sesi yang dihapus via Remove. Dipanggil saat user ketik .done.
func (c *SessionCache) Remove(phone string) *Session {
	c.mu.Lock()
	defer c.mu.Unlock()
	entry, ok := c.sessions[phone]
	if !ok {
		return nil
	}
	entry.timer.Stop()
	delete(c.sessions, phone)
	return entry.session
}

// Size jumlah sesi aktif. Untuk metrics / logging.
func (c *SessionCache) Size() int {
	c.mu.Lock()
	defer c.mu.Unlock()
	return len(c.sessions)
}
