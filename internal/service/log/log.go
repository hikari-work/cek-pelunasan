// Package log mengelola koleksi data_update_log — timestamp terakhir
// data jenis tertentu (TAGIHAN/SAVING) diperbarui. Dipakai bot untuk
// menampilkan peringatan kalau data yang ditampilkan bukan dari hari ini.
package log

import (
	"context"
	"time"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
	"github.com/hikari-work/cek-pelunasan/internal/repository"
)

// JakartaTZ adalah zona waktu yang dipakai semua timestamp di domain ini.
// fixed zone WIB +07:00 supaya tidak bergantung tzdata di runtime.
var JakartaTZ = time.FixedZone("WIB", 7*3600)

type Service struct {
	repo *repository.DataUpdateLogRepo
}

func NewService(repo *repository.DataUpdateLogRepo) *Service {
	return &Service{repo: repo}
}

func (s *Service) SaveUpdateTimestamp(ctx context.Context, dataType string) error {
	return s.repo.Save(ctx, &entity.DataUpdateLog{
		DataType:  dataType,
		UpdatedAt: time.Now().In(JakartaTZ),
	})
}

func (s *Service) Find(ctx context.Context, dataType string) (*entity.DataUpdateLog, error) {
	return s.repo.FindByID(ctx, dataType)
}
