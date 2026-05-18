// Package users mengelola data pengguna bot (chatId, role, branch).
// Padanan UserService.java + UserRepository tipis (sebagian akses langsung).
package users

import (
	"context"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
	"github.com/hikari-work/cek-pelunasan/internal/repository"
)

type Service struct {
	repo *repository.UserRepo
}

func NewService(repo *repository.UserRepo) *Service {
	return &Service{repo: repo}
}

// InsertNew mendaftarkan chatId baru dengan role default AO.
// Kalau chatId sudah ada, dokumen ditimpa.
func (s *Service) InsertNew(ctx context.Context, chatID int64) error {
	return s.repo.Save(ctx, &entity.User{ChatID: chatID, Roles: entity.RoleAO})
}

func (s *Service) Delete(ctx context.Context, chatID int64) error {
	return s.repo.DeleteByID(ctx, chatID)
}

func (s *Service) FindByChatID(ctx context.Context, chatID int64) (*entity.User, error) {
	return s.repo.FindByID(ctx, chatID)
}

func (s *Service) FindAll(ctx context.Context) ([]entity.User, error) {
	return s.repo.FindAll(ctx)
}

// FindBranch mengembalikan kode cabang yang tersimpan untuk pengguna.
// Mengembalikan ("", nil) kalau pengguna belum mengatur branch.
func (s *Service) FindBranch(ctx context.Context, chatID int64) (string, error) {
	u, err := s.repo.FindByID(ctx, chatID)
	if err != nil || u == nil {
		return "", err
	}
	return u.Branch, nil
}

func (s *Service) SaveBranch(ctx context.Context, chatID int64, branch string) error {
	u, err := s.repo.FindByID(ctx, chatID)
	if err != nil {
		return err
	}
	if u == nil {
		return nil
	}
	u.Branch = branch
	return s.repo.Save(ctx, u)
}

func (s *Service) Count(ctx context.Context) (int64, error) {
	users, err := s.repo.FindAll(ctx)
	if err != nil {
		return 0, err
	}
	return int64(len(users)), nil
}
