// Package slik berisi state in-memory untuk multi-step flow /slik & /doc,
// plus parser/formatter SLIK JSON dan helper path R2.
package slik

import (
	"sync"
	"time"
)

// PendingType jenis query yang menunggu pilih bulan.
//
//	"ktp"  — query cocok pola 16 digit (NIK)
//	"name" — query bebas (nama)
//	"doc"  — pengambilan dokumen via /doc
type PendingType string

const (
	TypeKTP  PendingType = "ktp"
	TypeName PendingType = "name"
	TypeDoc  PendingType = "doc"
)

const (
	pendingTTL = 30 * time.Minute
	sessionTTL = 30 * time.Minute
)

// PendingQuery menyimpan query yang menunggu pilih bulan.
type PendingQuery struct {
	Query     string
	Type      PendingType
	CreatedAt time.Time
}

// Session hasil pencarian SLIK by nama: list halaman + query asal.
type Session struct {
	Pages     []PageData
	Query     string
	ExpiresAt time.Time
}

// SessionCache map chatID -> pending + halaman hasil. Eviction lazy untuk pending,
// goroutine cleanup periodik untuk session (lebih banyak data).
type SessionCache struct {
	mu       sync.Mutex
	pending  map[int64]PendingQuery
	sessions map[int64]Session
}

func NewSessionCache() *SessionCache {
	return &SessionCache{
		pending:  make(map[int64]PendingQuery),
		sessions: make(map[int64]Session),
	}
}

// PutPending simpan query untuk chatID. Overwrite kalau sudah ada.
func (c *SessionCache) PutPending(chatID int64, q PendingQuery) {
	c.mu.Lock()
	defer c.mu.Unlock()
	q.CreatedAt = time.Now()
	c.pending[chatID] = q
}

// Get ambil pending. Mengembalikan ok=false kalau tidak ada atau sudah TTL.
func (c *SessionCache) Get(chatID int64) (PendingQuery, bool) {
	c.mu.Lock()
	defer c.mu.Unlock()
	q, ok := c.pending[chatID]
	if !ok {
		return PendingQuery{}, false
	}
	if time.Since(q.CreatedAt) > pendingTTL {
		delete(c.pending, chatID)
		return PendingQuery{}, false
	}
	return q, true
}

// TakePending ambil pending lalu hapus dari cache (one-shot).
func (c *SessionCache) TakePending(chatID int64) (PendingQuery, bool) {
	c.mu.Lock()
	defer c.mu.Unlock()
	q, ok := c.pending[chatID]
	delete(c.pending, chatID)
	if !ok {
		return PendingQuery{}, false
	}
	if time.Since(q.CreatedAt) > pendingTTL {
		return PendingQuery{}, false
	}
	return q, true
}

// Clear hapus pending milik chatID.
func (c *SessionCache) Clear(chatID int64) {
	c.mu.Lock()
	defer c.mu.Unlock()
	delete(c.pending, chatID)
}

// PutSession simpan hasil pencarian SLIK by nama untuk paginasi.
func (c *SessionCache) PutSession(chatID int64, pages []PageData, query string) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.sessions[chatID] = Session{
		Pages:     pages,
		Query:     query,
		ExpiresAt: time.Now().Add(sessionTTL),
	}
}

// GetSession ambil sesi pencarian. ok=false kalau tidak ada / TTL habis.
func (c *SessionCache) GetSession(chatID int64) (Session, bool) {
	c.mu.Lock()
	defer c.mu.Unlock()
	s, ok := c.sessions[chatID]
	if !ok {
		return Session{}, false
	}
	if time.Now().After(s.ExpiresAt) {
		delete(c.sessions, chatID)
		return Session{}, false
	}
	return s, true
}

// CleanupExpired buang sesi/pending yang sudah TTL. Aman dipanggil periodik.
func (c *SessionCache) CleanupExpired() (sessionsRemoved, pendingRemoved int) {
	c.mu.Lock()
	defer c.mu.Unlock()
	now := time.Now()
	for k, s := range c.sessions {
		if now.After(s.ExpiresAt) {
			delete(c.sessions, k)
			sessionsRemoved++
		}
	}
	for k, p := range c.pending {
		if now.Sub(p.CreatedAt) > pendingTTL {
			delete(c.pending, k)
			pendingRemoved++
		}
	}
	return
}
