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

// TelegramWarning mengembalikan banner italic Markdown jika data terakhir diupdate
// bukan hari ini (zona WIB). Kembali "" kalau up-to-date atau belum pernah diupdate.
func (s *Service) TelegramWarning(ctx context.Context, dataType string) string {
	return s.warning(ctx, dataType, true)
}

// WhatsAppWarning padanan plain-text — tanpa markdown.
func (s *Service) WhatsAppWarning(ctx context.Context, dataType string) string {
	return s.warning(ctx, dataType, false)
}

func (s *Service) warning(ctx context.Context, dataType string, asMarkdown bool) string {
	rec, err := s.repo.FindByID(ctx, dataType)
	if err != nil || rec == nil {
		return ""
	}
	updated := rec.UpdatedAt.In(JakartaTZ)
	today := time.Now().In(JakartaTZ)
	if updated.Year() == today.Year() && updated.YearDay() == today.YearDay() {
		return ""
	}
	stamp := updated.Format("02-01-2006")
	if asMarkdown {
		return "\n\n⚠️ _Data terakhir diupdate tanggal " + stamp + "_"
	}
	return "\n\n⚠️ Data terakhir diupdate tanggal " + stamp
}
