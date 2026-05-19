// Package slik berisi state in-memory untuk multi-step flow /slik & /doc.
// Sejak SLIK PDF generator belum diport, cache ini juga jadi entry point
// untuk callback handler yang akan datang.
package slik

import (
	"sync"
	"time"
)

// PendingType: jenis query yang menunggu pilih bulan.
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

const pendingTTL = 30 * time.Minute

// PendingQuery menyimpan query yang menunggu pilih bulan.
type PendingQuery struct {
	Query     string
	Type      PendingType
	CreatedAt time.Time
}

// SessionCache map chatID -> pending. Eviction lazy: dibuang saat Get setelah TTL.
type SessionCache struct {
	mu      sync.Mutex
	pending map[int64]PendingQuery
}

func NewSessionCache() *SessionCache {
	return &SessionCache{pending: make(map[int64]PendingQuery)}
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

// Clear hapus pending milik chatID.
func (c *SessionCache) Clear(chatID int64) {
	c.mu.Lock()
	defer c.mu.Unlock()
	delete(c.pending, chatID)
}
