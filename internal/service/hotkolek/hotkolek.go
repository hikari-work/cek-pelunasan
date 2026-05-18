// Package hotkolek menyediakan logic koleksi khusus cabang 1075 (kios).
// Tiga kategori: jatuh tempo bulan ini, pembayaran pertama bulan lalu,
// dan nasabah dengan minimal pay. Selalu menyaring SPK yang sudah lunas.
package hotkolek

import (
	"context"
	"strconv"
	"strings"
	"time"

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

// FindDueDate mengembalikan tagihan jatuh tempo bulan ini yang belum lunas,
// difilter sesuai aturan kios.
func (s *Service) FindDueDate(ctx context.Context, kiosCode string) ([]entity.Bills, error) {
	bills, err := s.bills.FindByBranchAndDueDatePrefix(ctx, TargetBranch, currentMonth())
	if err != nil {
		return nil, err
	}
	return s.filterByKios(ctx, bills, kiosCode)
}

// FindFirstPay: nasabah yang baru pertama bayar bulan lalu (dilihat dari realization).
func (s *Service) FindFirstPay(ctx context.Context, kiosCode string) ([]entity.Bills, error) {
	bills, err := s.bills.FindByBranchAndRealizationPrefix(ctx, TargetBranch, previousMonth())
	if err != nil {
		return nil, err
	}
	return s.filterByKios(ctx, bills, kiosCode)
}

// FindMinimalPay mengembalikan nasabah dengan minimal bayar. Aturan dari Java:
//   - kosong/sama dengan TargetBranch -> tagihan branch saja (tanpa kios)
//   - selainnya -> branch + kios spesifik
//
// Setelah filter kios, buang juga tagihan dengan dayLate > 125 atau dayLate non-numeric.
func (s *Service) FindMinimalPay(ctx context.Context, kiosCode string) ([]entity.Bills, error) {
	var bills []entity.Bills
	var err error
	if kiosCode == "" || kiosCode == TargetBranch {
		bills, err = s.bills.FindByBranchAndTotalMin(ctx, TargetBranch, 0, repository.Page{Size: 0})
	} else {
		bills, err = s.bills.FindByBranchAndKiosAndTotalMin(ctx, TargetBranch, kiosCode, 0, repository.Page{Size: 0})
	}
	if err != nil {
		return nil, err
	}

	filtered, err := s.filterByKios(ctx, bills, kiosCode)
	if err != nil {
		return nil, err
	}
	out := filtered[:0]
	for _, b := range filtered {
		if isValidForCollection(b) {
			out = append(out, b)
		}
	}
	return out, nil
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

// filterByKios menerapkan aturan filter kios + buang SPK yang sudah lunas + pastikan
// hasil hanya dari TargetBranch (1075).
//
// Aturan kios (dari Java):
//   - kiosCode == TargetBranch  -> sisakan yang field kios kosong/null
//   - kiosCode != ""            -> hanya yang kios == kiosCode
//   - kiosCode == ""            -> tanpa filter tambahan
func (s *Service) filterByKios(ctx context.Context, bills []entity.Bills, kiosCode string) ([]entity.Bills, error) {
	if len(bills) == 0 {
		return bills, nil
	}
	paidIDs, err := s.paying.FindAllIDs(ctx)
	if err != nil {
		return nil, err
	}
	paidSet := make(map[string]struct{}, len(paidIDs))
	for _, id := range paidIDs {
		paidSet[id] = struct{}{}
	}

	out := bills[:0]
	for _, b := range bills {
		if _, lunas := paidSet[b.NoSpk]; lunas {
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
		if b.Branch != TargetBranch {
			continue
		}
		out = append(out, b)
	}
	return out, nil
}

func isValidForCollection(b entity.Bills) bool {
	dl, err := strconv.Atoi(strings.TrimSpace(b.DayLate))
	if err != nil {
		return false
	}
	return dl <= 125
}
