// Package bill menyediakan layer service untuk koleksi tagihan (bills).
// Operasi yang didukung mencakup query terpaginasi, parse + import CSV,
// dan helper distinct (cabang, AO).
package bill

import (
	"context"
	"fmt"
	"math/big"
	"strconv"
	"strings"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
	"github.com/hikari-work/cek-pelunasan/internal/repository"
	"github.com/hikari-work/cek-pelunasan/internal/service/csvimport"
	logsvc "github.com/hikari-work/cek-pelunasan/internal/service/log"
	"go.mongodb.org/mongo-driver/v2/bson"
)

type Service struct {
	repo    *repository.BillsRepo
	updates *logsvc.Service
}

func NewService(repo *repository.BillsRepo, updates *logsvc.Service) *Service {
	return &Service{repo: repo, updates: updates}
}

// Page generik untuk hasil query terpaginasi.
type PageResult[T any] struct {
	Items []T
	Total int64
	Page  int64
	Size  int64
}

func (s *Service) GetByID(ctx context.Context, id string) (*entity.Bills, error) {
	return s.repo.FindByID(ctx, id)
}

func (s *Service) FindByAOAndPayDown(ctx context.Context, ao, payDown string, page, size int64) (PageResult[entity.Bills], error) {
	p := repository.Page{Page: page, Size: size}
	items, err := s.repo.FindByAccountOfficerAndPayDown(ctx, ao, payDown, p)
	if err != nil {
		return PageResult[entity.Bills]{}, err
	}
	total, err := s.repo.CountByAccountOfficerAndPayDown(ctx, ao, payDown)
	if err != nil {
		return PageResult[entity.Bills]{}, err
	}
	return PageResult[entity.Bills]{Items: items, Total: total, Page: page, Size: size}, nil
}

func (s *Service) FindByBranchAndPayDown(ctx context.Context, branch, payDown string, page, size int64) (PageResult[entity.Bills], error) {
	p := repository.Page{Page: page, Size: size}
	items, err := s.repo.FindByBranchAndPayDownOrderByAO(ctx, branch, payDown, p)
	if err != nil {
		return PageResult[entity.Bills]{}, err
	}
	total, err := s.repo.CountByBranchAndPayDown(ctx, branch, payDown)
	if err != nil {
		return PageResult[entity.Bills]{}, err
	}
	return PageResult[entity.Bills]{Items: items, Total: total, Page: page, Size: size}, nil
}

func (s *Service) FindByNameAndBranch(ctx context.Context, name, branch string, page, size int64) (PageResult[entity.Bills], error) {
	p := repository.Page{Page: page, Size: size}
	items, err := s.repo.FindByNameAndBranch(ctx, name, branch, p)
	if err != nil {
		return PageResult[entity.Bills]{}, err
	}
	total, err := s.repo.CountByNameAndBranch(ctx, name, branch)
	if err != nil {
		return PageResult[entity.Bills]{}, err
	}
	return PageResult[entity.Bills]{Items: items, Total: total, Page: page, Size: size}, nil
}

func (s *Service) FindByName(ctx context.Context, name string, page, size int64) (PageResult[entity.Bills], error) {
	p := repository.Page{Page: page, Size: size}
	items, err := s.repo.FindByName(ctx, name, p)
	if err != nil {
		return PageResult[entity.Bills]{}, err
	}
	total, err := s.repo.CountByName(ctx, name)
	if err != nil {
		return PageResult[entity.Bills]{}, err
	}
	return PageResult[entity.Bills]{Items: items, Total: total, Page: page, Size: size}, nil
}

// FindMinimalPaymentByBranch — port apa adanya dari Java.
// Catatan: di legacy, parameter "branch" dipakai sebagai kios di
// findByKiosAndTotalMin. Pertahankan kuirk-nya.
func (s *Service) FindMinimalPaymentByBranch(ctx context.Context, branch string, page, size int64) (PageResult[entity.Bills], error) {
	p := repository.Page{Page: page, Size: size}
	items, err := s.repo.FindByKiosAndTotalMin(ctx, branch, 0, p)
	if err != nil {
		return PageResult[entity.Bills]{}, err
	}
	return PageResult[entity.Bills]{Items: items, Total: 0, Page: page, Size: size}, nil
}

func (s *Service) FindMinimalPaymentByAO(ctx context.Context, officer string, page, size int64) (PageResult[entity.Bills], error) {
	p := repository.Page{Page: page, Size: size}
	items, err := s.repo.FindByAOWithMinTunggakan(ctx, officer, p)
	if err != nil {
		return PageResult[entity.Bills]{}, err
	}
	total, err := s.repo.CountByAOWithMinTunggakan(ctx, officer)
	if err != nil {
		return PageResult[entity.Bills]{}, err
	}
	return PageResult[entity.Bills]{Items: items, Total: total, Page: page, Size: size}, nil
}

func (s *Service) FindAllByBranch(ctx context.Context, branch string) ([]entity.Bills, error) {
	return s.repo.FindAllByBranch(ctx, branch)
}

// MinBunga: branch + minInterest > 0 + collectStatus=02 + product != KUBTP, dayLate dalam [minDayLate, 120).
func (s *Service) FindMinimalBungaByBranch(ctx context.Context, branch string, minDayLate int) ([]entity.Bills, error) {
	return s.repo.FindByBranchAndMinInterestAndDayLateBetween(ctx, branch, minDayLate, 120)
}

func (s *Service) FindMinimalBungaByAO(ctx context.Context, ao string, minDayLate int) ([]entity.Bills, error) {
	return s.repo.FindByAOAndMinInterestAndDayLateBetween(ctx, ao, minDayLate, 120)
}

// ListAllBranches mengembalikan distinct nama cabang dari koleksi tagihan.
func (s *Service) ListAllBranches(ctx context.Context) ([]string, error) {
	return s.distinct(ctx, "branch")
}

// ListAllAccountOfficers mengembalikan distinct nama AO.
func (s *Service) ListAllAccountOfficers(ctx context.Context) ([]string, error) {
	return s.distinct(ctx, "accountOfficer")
}

func (s *Service) distinct(ctx context.Context, field string) ([]string, error) {
	coll := s.repo.Collection()
	res := coll.Distinct(ctx, field, bson.M{})
	var raw []any
	if err := res.Decode(&raw); err != nil {
		return nil, err
	}
	out := make([]string, 0, len(raw))
	for _, v := range raw {
		if str, ok := v.(string); ok && str != "" {
			out = append(out, str)
		}
	}
	return out, nil
}

// ProgressFn dipanggil setelah satu batch berhasil disimpan, dengan jumlah
// baris yang sudah diproses sampai saat ini. Boleh nil.
type ProgressFn = csvimport.ProgressFn

// ParseCSVAndSave membaca CSV, hapus seluruh data lama, lalu insert ulang
// secara batch (500 baris). Setelah selesai, simpan timestamp ke DataUpdateLog.
//
// Throttle progress: callback hanya dipanggil ~20 kali (per 5% dari total).
func (s *Service) ParseCSVAndSave(ctx context.Context, path string, total int64, onProgress ProgressFn) error {
	if err := s.repo.DeleteAll(ctx); err != nil {
		return fmt.Errorf("delete all bills: %w", err)
	}
	if err := csvimport.Run(
		ctx, path, false, total,
		func(row []string) (entity.Bills, bool) { return mapRowToBill(row), true },
		s.repo.InsertMany,
		onProgress,
	); err != nil {
		return err
	}
	return s.updates.SaveUpdateTimestamp(ctx, "TAGIHAN")
}

// mapRowToBill: mengikuti urutan kolom dari Java (BillService.mapToBill).
// Kolom 27 (line[27]) di-skip seperti aslinya.
func mapRowToBill(row []string) entity.Bills {
	get := func(i int) string {
		if i < len(row) {
			return row[i]
		}
		return ""
	}
	parseLong := func(s string) int64 {
		n, err := strconv.ParseInt(strings.TrimSpace(s), 10, 64)
		if err != nil {
			return 0
		}
		return n
	}
	parseBigInt := func(s string) int64 {
		s = strings.TrimSpace(s)
		if s == "" {
			return 0
		}
		// big.Int -> int64; nominal CKPN bisa besar tapi muat di int64.
		bi, ok := new(big.Int).SetString(s, 10)
		if !ok {
			return 0
		}
		return bi.Int64()
	}

	return entity.Bills{
		CustomerID:        get(0),
		Wilayah:           get(1),
		Branch:            get(2),
		NoSpk:             get(3),
		OfficeLocation:    get(4),
		Product:           get(5),
		Name:              get(6),
		Address:           get(7),
		PayDown:           get(8),
		Realization:       get(9),
		DueDate:           get(10),
		CollectStatus:     get(11),
		DayLate:           get(12),
		Plafond:           parseLong(get(13)),
		DebitTray:         parseLong(get(14)),
		Interest:          parseLong(get(15)),
		Principal:         parseLong(get(16)),
		Installment:       parseLong(get(17)),
		LastInterest:      parseLong(get(18)),
		LastPrincipal:     parseLong(get(19)),
		LastInstallment:   parseLong(get(20)),
		FullPayment:       parseLong(get(21)),
		MinInterest:       parseLong(get(22)),
		MinPrincipal:      parseLong(get(23)),
		PenaltyInterest:   parseLong(get(24)),
		PenaltyPrincipal:  parseLong(get(25)),
		AccountOfficer:    get(26),
		Kios:              get(28),
		Titipan:           parseLong(get(29)),
		FixedInterest:     parseLong(get(30)),
		CKPNType:          get(31),
		CKPNNominal:       parseBigInt(get(32)),
		RekeningAutobedet: get(33),
	}
}
