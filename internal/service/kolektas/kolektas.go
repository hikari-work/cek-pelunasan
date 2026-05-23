// Package kolektas mengelola koleksi tagihan kelompok (kolek_tas).
package kolektas

import (
	"context"
	"strconv"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
	"github.com/hikari-work/cek-pelunasan/internal/repository"
	"github.com/hikari-work/cek-pelunasan/internal/service/csvimport"
	"github.com/hikari-work/cek-pelunasan/internal/utils"
)

type Service struct {
	repo *repository.KolekTasRepo
}

func NewService(repo *repository.KolekTasRepo) *Service {
	return &Service{repo: repo}
}

type PageResult struct {
	Items []entity.KolekTas
	Total int64
	Page  int64
	Size  int64
}

// FindByKelompok page 1-based (sesuai konvensi tampilan bot).
func (s *Service) FindByKelompok(ctx context.Context, kelompok string, page, size int64) (PageResult, error) {
	zero := page - 1
	if zero < 0 {
		zero = 0
	}
	p := repository.Page{Page: zero, Size: size}
	items, err := s.repo.FindByKelompok(ctx, kelompok, p)
	if err != nil {
		return PageResult{}, err
	}
	total, err := s.repo.CountByKelompok(ctx, kelompok)
	if err != nil {
		return PageResult{}, err
	}
	return PageResult{Items: items, Total: total, Page: page, Size: size}, nil
}

func (s *Service) FindByID(ctx context.Context, id string) (*entity.KolekTas, error) {
	return s.repo.FindByID(ctx, id)
}

func (s *Service) ParseCSVAndSave(ctx context.Context, path string, total int64, onProgress csvimport.ProgressFn) error {
	if err := s.repo.DeleteAll(ctx); err != nil {
		return err
	}
	return csvimport.Run(
		ctx, path, true, total,
		func(row []string) (entity.KolekTas, bool) {
			if len(row) < 10 {
				return entity.KolekTas{}, false
			}
			nominal, _ := strconv.ParseInt(row[7], 10, 64)
			return entity.KolekTas{
				Kelompok:       row[0],
				Kantor:         row[1],
				Rekening:       row[2],
				Nama:           row[3],
				Alamat:         row[4],
				NoHP:           row[5],
				Kolek:          row[6],
				Nominal:        utils.FormatRupiah(nominal),
				AccountOfficer: row[8],
				CIF:            row[9],
			}, true
		},
		s.repo.InsertMany,
		onProgress,
	)
}
