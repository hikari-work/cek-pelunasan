// Package hotkolek menyediakan logic koleksi khusus cabang 1075 (kios).
// Tiga kategori: jatuh tempo bulan ini, pembayaran pertama bulan lalu,
// dan nasabah dengan minimal pay. Selalu menyaring SPK yang sudah lunas.
package hotkolek

import (
	"context"
	"strconv"
	"strings"
	"time"

	"golang.org/x/sync/errgroup"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
	"github.com/hikari-work/cek-pelunasan/internal/repository"
)

const TargetBranch = "1075"

// jakartaTZ: WIB +07 fixed, supaya getMonth/getLastMonth deterministik.
var jakartaTZ = time.FixedZone("WIB", 7*3600)

type Service struct {
	bills  *repository.BillsRepo
	paying *repository.PayingRepo
}

func NewService(bills *repository.BillsRepo, paying *repository.PayingRepo) *Service {
	return &Service{bills: bills, paying: paying}
}

func currentMonth() string  { return time.Now().In(jakartaTZ).Format("2006-01") }
func previousMonth() string { return time.Now().In(jakartaTZ).AddDate(0, -1, 0).Format("2006-01") }

// LocationCategories: tagihan di satu kios, dibagi 3 kategori sesuai legacy.
type LocationCategories struct {
	MinimalPay []entity.Bills
	FirstPay   []entity.Bills
	DueDate    []entity.Bills
}

// BuildLocations ambil 4 dataset (paid IDs + 3 query bills) secara paralel,
// lalu partisi hasilnya per kios di Go. Lebih cepat dari versi lama yang
// nge-loop 3 kios × 3 query = 9 round-trip + N call FindAllIDs lagi di
// dalam filterByKios.
//
// Untuk MinimalPay path "branch-only" (kios kosong/sama dengan TargetBranch),
// kita query tanpa filter kios; sisanya difilter di memori per kiosCode.
func (s *Service) BuildLocations(ctx context.Context, kiosCodes []string) (map[string]LocationCategories, error) {
	var (
		paidIDs    []string
		dueBills   []entity.Bills
		firstBills []entity.Bills
		minBills   []entity.Bills
	)

	g, gCtx := errgroup.WithContext(ctx)
	g.Go(func() error {
		ids, err := s.paying.FindAllIDs(gCtx)
		if err != nil {
			return err
		}
		paidIDs = ids
		return nil
	})
	g.Go(func() error {
		b, err := s.bills.FindByBranchAndDueDatePrefix(gCtx, TargetBranch, currentMonth())
		if err != nil {
			return err
		}
		dueBills = b
		return nil
	})
	g.Go(func() error {
		b, err := s.bills.FindByBranchAndRealizationPrefix(gCtx, TargetBranch, previousMonth())
		if err != nil {
			return err
		}
		firstBills = b
		return nil
	})
	g.Go(func() error {
		b, err := s.bills.FindByBranchAndTotalMin(gCtx, TargetBranch, 0, repository.Page{Size: 0})
		if err != nil {
			return err
		}
		minBills = b
		return nil
	})
	if err := g.Wait(); err != nil {
		return nil, err
	}

	paidSet := make(map[string]struct{}, len(paidIDs))
	for _, id := range paidIDs {
		paidSet[id] = struct{}{}
	}

	out := make(map[string]LocationCategories, len(kiosCodes))
	for _, code := range kiosCodes {
		out[code] = LocationCategories{
			MinimalPay: filterMinimalPay(minBills, paidSet, code),
			FirstPay:   filterByKiosSet(firstBills, paidSet, code),
			DueDate:    filterByKiosSet(dueBills, paidSet, code),
		}
	}
	return out, nil
}

func filterByKiosSet(bills []entity.Bills, paidSet map[string]struct{}, kiosCode string) []entity.Bills {
	out := make([]entity.Bills, 0, len(bills))
	for _, b := range bills {
		if _, lunas := paidSet[b.NoSpk]; lunas {
			continue
		}
		if b.Branch != TargetBranch {
			continue
		}
		switch {
		case kiosCode == TargetBranch:
			if strings.TrimSpace(b.Kios) != "" {
				continue
			}
		case kiosCode != "":
			if b.Kios != kiosCode {
				continue
			}
		}
		out = append(out, b)
	}
	return out
}

func filterMinimalPay(bills []entity.Bills, paidSet map[string]struct{}, kiosCode string) []entity.Bills {
	filtered := filterByKiosSet(bills, paidSet, kiosCode)
	out := filtered[:0]
	for _, b := range filtered {
		if isValidForCollection(b) {
			out = append(out, b)
		}
	}
	return out
}

// FindUnpaidByBranch mengembalikan tagihan suatu cabang yang SPK-nya tidak ada
// di koleksi paying.
func (s *Service) FindUnpaidByBranch(ctx context.Context, branch string) ([]entity.Bills, error) {
	paid, err := s.paying.FindAllIDs(ctx)
	if err != nil {
		return nil, err
	}
	if len(paid) == 0 {
		return s.bills.FindAllByBranch(ctx, branch)
	}
	return s.bills.FindByBranchAndNoSpkNotIn(ctx, branch, paid)
}

// SaveAllPaying menandai daftar SPK sebagai lunas.
func (s *Service) SaveAllPaying(ctx context.Context, spks []string) error {
	for _, spk := range spks {
		if err := s.paying.Save(ctx, &entity.Paying{ID: spk, Paid: true}); err != nil {
			return err
		}
	}
	return nil
}

func isValidForCollection(b entity.Bills) bool {
	dl, err := strconv.Atoi(strings.TrimSpace(b.DayLate))
	if err != nil {
		return false
	}
	return dl <= 125
}
