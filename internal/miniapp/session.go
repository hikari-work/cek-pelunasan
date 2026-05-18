package miniapp

import (
	"crypto/rand"
	"encoding/hex"
	"sync"
	"time"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
)

// session mewakili pengguna yang sudah lolos verifikasi initData.
// Token in-memory: hilang saat restart, sengaja dibiarkan agar
// re-auth otomatis lewat Telegram WebApp tanpa state lintas-server.
type session struct {
	Token     string
	ChatID    int64
	Roles     entity.Role
	ExpiresAt time.Time
}

type sessionStore struct {
	mu  sync.RWMutex
	ttl time.Duration
	m   map[string]session
}

func newSessionStore(ttlMinutes int) *sessionStore {
	return &sessionStore{
		ttl: time.Duration(ttlMinutes) * time.Minute,
		m:   make(map[string]session),
	}
}

func (s *sessionStore) Create(chatID int64, roles entity.Role) session {
	tok := randToken()
	sess := session{
		Token:     tok,
		ChatID:    chatID,
		Roles:     roles,
		ExpiresAt: time.Now().Add(s.ttl),
	}
	s.mu.Lock()
	s.m[tok] = sess
	s.mu.Unlock()
	return sess
}

func (s *sessionStore) Get(token string) (session, bool) {
	if token == "" {
		return session{}, false
	}
	s.mu.RLock()
	sess, ok := s.m[token]
	s.mu.RUnlock()
	if !ok {
		return session{}, false
	}
	if time.Now().After(sess.ExpiresAt) {
		s.mu.Lock()
		delete(s.m, token)
		s.mu.Unlock()
		return session{}, false
	}
	return sess, true
}

// Cleanup menghapus session expired. Panggil dari goroutine periodik.
func (s *sessionStore) Cleanup() int {
	now := time.Now()
	s.mu.Lock()
	defer s.mu.Unlock()
	removed := 0
	for k, v := range s.m {
		if now.After(v.ExpiresAt) {
			delete(s.m, k)
			removed++
		}
	}
	return removed
}

func randToken() string {
	var b [16]byte
	_, _ = rand.Read(b[:])
	return hex.EncodeToString(b[:])
}
