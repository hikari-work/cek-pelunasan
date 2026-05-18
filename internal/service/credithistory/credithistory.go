// Package credithistory mengelola koleksi credit_history (riwayat SLIK historis).
package credithistory

import (
	"context"
	"strconv"
	"strings"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
	"github.com/hikari-work/cek-pelunasan/internal/repository"
	"github.com/hikari-work/cek-pelunasan/internal/service/csvimport"
	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo/options"
)

type Service struct {
	repo *repository.CreditHistoryRepo
}

func NewService(repo *repository.CreditHistoryRepo) *Service {
	return &Service{repo: repo}
}

type PageResult struct {
	Items []entity.CreditHistory
	Total int64
	Page  int64
	Size  int64
}

// SearchAddressByKeywords: cari riwayat berdasarkan kata kunci alamat (semua AND),
// kecuali nasabah yang status-nya "A" (aktif). 5 hasil per halaman.
func (s *Service) SearchAddressByKeywords(ctx context.Context, keywords []string, page int64) (PageResult, error) {
	const size int64 = 5

	coll := s.repo.Collection()

	activeRes := coll.Distinct(ctx, "customerId", bson.M{"status": bson.M{"$regex": "^A$", "$options": "i"}})
	var activeRaw []any
	if err := activeRes.Decode(&activeRaw); err != nil {
		return PageResult{}, err
	}
	activeIDs := make([]string, 0, len(activeRaw))
	for _, v := range activeRaw {
		if str, ok := v.(string); ok {
			activeIDs = append(activeIDs, str)
		}
	}

	addressFilter := bson.A{}
	for _, kw := range keywords {
		kw = strings.TrimSpace(kw)
		if kw == "" {
			continue
		}
		addressFilter = append(addressFilter, bson.M{"address": bson.M{"$regex": kw, "$options": "i"}})
	}

	combined := bson.M{"customerId": bson.M{"$nin": activeIDs}}
	if len(addressFilter) > 0 {
		combined["$and"] = addressFilter
	}

	skip := page * size
	cur, err := coll.Find(ctx, combined, options.Find().SetSkip(skip).SetLimit(size))
	if err != nil {
		return PageResult{}, err
	}
	defer cur.Close(ctx)
	var items []entity.CreditHistory
	if err := cur.All(ctx, &items); err != nil {
		return PageResult{}, err
	}

	total, err := coll.CountDocuments(ctx, combined)
	if err != nil {
		return PageResult{}, err
	}

	return PageResult{Items: items, Total: total, Page: page, Size: size}, nil
}

func (s *Service) Count(ctx context.Context) (int64, error) {
	return s.repo.Count(ctx)
}

// ParseCSVAndSave: hapus semua, lalu re-insert dari CSV.
// Format kolom: date(epoch), creditId, customerId, name, status, address, phone.
func (s *Service) ParseCSVAndSave(ctx context.Context, path string, total int64, onProgress csvimport.ProgressFn) error {
	if err := s.repo.DeleteAll(ctx); err != nil {
		return err
	}
	return csvimport.Run(
		ctx, path, false, total,
		func(row []string) (entity.CreditHistory, bool) {
			if len(row) < 7 {
				return entity.CreditHistory{}, false
			}
			date, _ := strconv.ParseInt(strings.TrimSpace(row[0]), 10, 64)
			return entity.CreditHistory{
				Date:       date,
				CreditID:   row[1],
				CustomerID: row[2],
				Name:       row[3],
				Status:     row[4],
				Address:    row[5],
				Phone:      row[6],
			}, true
		},
		s.repo.InsertMany,
		onProgress,
	)
}
