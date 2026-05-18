// Package auth menyimpan daftar chatId yang diizinkan mengakses bot dalam memori.
// Padanan AuthorizedChats.java — preload sekali saat startup, lalu update lewat
// AddChat/RemoveChat saat admin mendaftar/menghapus pengguna.
package auth

import (
	"context"
	"sync"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
	"github.com/hikari-work/cek-pelunasan/internal/repository"
)

type AuthorizedChats struct {
	repo  *repository.UserRepo
	mu    sync.RWMutex
	chats map[int64]struct{}
}

func New(repo *repository.UserRepo) *AuthorizedChats {
	return &AuthorizedChats{
		repo:  repo,
		chats: make(map[int64]struct{}),
	}
}

// Preload memuat semua chatId dari database ke memori. Panggil sekali saat startup,
// sebelum bot mulai menerima update.
func (a *AuthorizedChats) Preload(ctx context.Context) error {
	users, err := a.repo.FindAll(ctx)
	if err != nil {
		return err
	}
	a.mu.Lock()
	defer a.mu.Unlock()
	for _, u := range users {
		a.chats[u.ChatID] = struct{}{}
	}
	return nil
}

func (a *AuthorizedChats) IsAuthorized(chatID int64) bool {
	a.mu.RLock()
	defer a.mu.RUnlock()
	_, ok := a.chats[chatID]
	return ok
}

func (a *AuthorizedChats) Size() int {
	a.mu.RLock()
	defer a.mu.RUnlock()
	return len(a.chats)
}

func (a *AuthorizedChats) AddChat(chatID int64) {
	a.mu.Lock()
	defer a.mu.Unlock()
	a.chats[chatID] = struct{}{}
}

func (a *AuthorizedChats) RemoveChat(chatID int64) {
	a.mu.Lock()
	defer a.mu.Unlock()
	delete(a.chats, chatID)
}

// Roles mengambil role pengguna dari MongoDB. Mengembalikan "" kalau pengguna
// tidak ditemukan atau belum punya role.
func (a *AuthorizedChats) Roles(ctx context.Context, chatID int64) (entity.Role, error) {
	u, err := a.repo.FindByID(ctx, chatID)
	if err != nil || u == nil {
		return "", err
	}
	return u.Roles, nil
}
