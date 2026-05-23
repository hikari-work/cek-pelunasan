// Package paymentdetails mengelola koleksi payment_details (data angsuran per AO/cabang/SPK).
package paymentdetails

import (
	"context"
	"fmt"
	"strconv"
	"strings"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
	"github.com/hikari-work/cek-pelunasan/internal/repository"
	"github.com/hikari-work/cek-pelunasan/internal/service/csvimport"
	logsvc "github.com/hikari-work/cek-pelunasan/internal/service/log"
)

const dataType = "PAYMENT_DETAILS"

type Service struct {
	repo    *repository.PaymentDetailsRepo
	updates *logsvc.Service
}

func NewService(repo *repository.PaymentDetailsRepo, updates *logsvc.Service) *Service {
	return &Service{repo: repo, updates: updates}
}

type PageResult struct {
	Items []entity.PaymentDetails
	Total int64
	Page  int64
	Size  int64
}

func (s *Service) FindByKodeAO(ctx context.Context, kodeAO string, page, size int64) (PageResult, error) {
	zero := page - 1
	if zero < 0 {
		zero = 0
	}
	p := repository.Page{Page: zero, Size: size}
	items, err := s.repo.FindByKodeAO(ctx, kodeAO, p)
	if err != nil {
		return PageResult{}, err
	}
	total, err := s.repo.CountByKodeAO(ctx, kodeAO)
	if err != nil {
		return PageResult{}, err
	}
	return PageResult{Items: items, Total: total, Page: page, Size: size}, nil
}

func (s *Service) FindByCabangAndTanggal(ctx context.Context, cabang, tanggal string, page, size int64) (PageResult, error) {
	zero := page - 1
	if zero < 0 {
		zero = 0
	}
	p := repository.Page{Page: zero, Size: size}
	items, err := s.repo.FindByKodeCabangAndTanggal(ctx, cabang, tanggal, p)
	if err != nil {
		return PageResult{}, err
	}
	total, err := s.repo.CountByKodeCabangAndTanggal(ctx, cabang, tanggal)
	if err != nil {
		return PageResult{}, err
	}
	return PageResult{Items: items, Total: total, Page: page, Size: size}, nil
}

func (s *Service) FindPelunasanByTanggal(ctx context.Context, tanggal string, page, size int64) (PageResult, error) {
	zero := page - 1
	if zero < 0 {
		zero = 0
	}
	p := repository.Page{Page: zero, Size: size}
	items, err := s.repo.FindByTanggalAndFlagPelunasan(ctx, tanggal, true, p)
	if err != nil {
		return PageResult{}, err
	}
	total, err := s.repo.CountByTanggalAndFlagPelunasan(ctx, tanggal, true)
	if err != nil {
		return PageResult{}, err
	}
	return PageResult{Items: items, Total: total, Page: page, Size: size}, nil
}

func (s *Service) FindByKodeAOAndTanggal(ctx context.Context, kodeAO, tanggal string) ([]entity.PaymentDetails, error) {
	return s.repo.FindByKodeAOAndTanggal(ctx, kodeAO, tanggal)
}

func (s *Service) FindByNoSpk(ctx context.Context, noSpk string) ([]entity.PaymentDetails, error) {
	return s.repo.FindByNoSpkOrdered(ctx, noSpk)
}

func (s *Service) ParseCSVAndSave(ctx context.Context, path string, total int64, onProgress csvimport.ProgressFn) error {
	if err := csvimport.Run(
		ctx, path, true, total,
		mapRow,
		s.repo.UpsertMany,
		onProgress,
	); err != nil {
		return err
	}
	return s.updates.SaveUpdateTimestamp(ctx, dataType)
}

func mapRow(row []string) (entity.PaymentDetails, bool) {
	if len(row) < 9 {
		return entity.PaymentDetails{}, false
	}
	parseLong := func(s string) int64 {
		n, _ := strconv.ParseInt(strings.TrimSpace(s), 10, 64)
		return n
	}
	tanggal := strings.TrimSpace(row[0])
	cabang := strings.TrimSpace(row[1])
	ao := strings.TrimSpace(row[2])
	spk := strings.TrimSpace(row[3])
	nama := strings.TrimSpace(row[4])
	posting := strings.TrimSpace(row[5])
	nominal := parseLong(row[6])
	denda := parseLong(row[7])
	penalti := parseLong(row[8])

	id := fmt.Sprintf("%s|%s|%s|%s|%s|%s|%d|%d|%d", tanggal, cabang, ao, spk, nama, posting, nominal, denda, penalti)
	return entity.PaymentDetails{
		ID:              id,
		Tanggal:         tanggal,
		KodeCabang:      cabang,
		KodeAO:          ao,
		NoSpk:           spk,
		Nama:            nama,
		KodePosting:     posting,
		NominalAngsuran: nominal,
		Denda:           denda,
		Penalti:         penalti,
		FlagPelunasan:   denda > 0 || penalti > 0,
	}, true
}
