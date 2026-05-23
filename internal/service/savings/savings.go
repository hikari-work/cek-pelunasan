// Package savings mengelola koleksi savings (rekening tabungan).
package savings

import (
	"context"
	"sort"
	"strconv"
	"strings"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
	"github.com/hikari-work/cek-pelunasan/internal/repository"
	"github.com/hikari-work/cek-pelunasan/internal/service/csvimport"
	logsvc "github.com/hikari-work/cek-pelunasan/internal/service/log"
	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo/options"
)

type Service struct {
	repo    *repository.SavingsRepo
	bills   *repository.BillsRepo
	updates *logsvc.Service
}

func NewService(repo *repository.SavingsRepo, bills *repository.BillsRepo, updates *logsvc.Service) *Service {
	return &Service{repo: repo, bills: bills, updates: updates}
}

type PageResult struct {
	Items []entity.Savings
	Total int64
	Page  int64
	Size  int64
}

func (s *Service) FindByNameAndBranch(ctx context.Context, name, branch string, page int64) (PageResult, error) {
	const size int64 = 5
	p := repository.Page{Page: page, Size: size}
	items, err := s.repo.FindByNameAndBranch(ctx, name, branch, p)
	if err != nil {
		return PageResult{}, err
	}
	total, err := s.repo.CountByNameAndBranch(ctx, name, branch)
	if err != nil {
		return PageResult{}, err
	}
	return PageResult{Items: items, Total: total, Page: page, Size: size}, nil
}

func (s *Service) FindByCIF(ctx context.Context, cif string) (*entity.Savings, error) {
	return s.repo.FindByCIF(ctx, cif)
}

func (s *Service) FindByID(ctx context.Context, tabID string) (*entity.Savings, error) {
	return s.repo.FindByTabID(ctx, tabID)
}

// FindByName: cari semua rekening dengan name regex. limit<=0 berarti tanpa batas.
func (s *Service) FindByName(ctx context.Context, name string, limit int) ([]entity.Savings, error) {
	rows, err := s.repo.FindByName(ctx, name)
	if err != nil {
		return nil, err
	}
	if limit > 0 && len(rows) > limit {
		rows = rows[:limit]
	}
	return rows, nil
}

// ListBranches: distinct branch dari savings yang name-nya match.
func (s *Service) ListBranches(ctx context.Context, name string) ([]string, error) {
	res := s.repo.Collection().Distinct(ctx, "branch", bson.M{"name": bson.M{"$regex": name, "$options": "i"}})
	var raw []any
	if err := res.Decode(&raw); err != nil {
		return nil, err
	}
	out := make([]string, 0, len(raw))
	for _, v := range raw {
		if str, ok := v.(string); ok && strings.TrimSpace(str) != "" {
			out = append(out, str)
		}
	}
	sort.Strings(out)
	return out, nil
}

// FindFiltered: cari savings by alamat (semua AND), kecuali CIF yang ada di Bills.
// Dedupe per CIF (ambil dokumen pertama saja).
func (s *Service) FindFiltered(ctx context.Context, addressKeywords []string, page, size int64) (PageResult, error) {
	billsCollRes := s.bills.Collection().Distinct(ctx, "customerId", bson.M{})
	var rawBillsCifs []any
	if err := billsCollRes.Decode(&rawBillsCifs); err != nil {
		return PageResult{}, err
	}
	billsCifs := make([]string, 0, len(rawBillsCifs))
	for _, v := range rawBillsCifs {
		if str, ok := v.(string); ok && strings.TrimSpace(str) != "" {
			billsCifs = append(billsCifs, str)
		}
	}

	addressAnd := bson.A{}
	for _, kw := range addressKeywords {
		kw = strings.TrimSpace(kw)
		if kw == "" {
			continue
		}
		addressAnd = append(addressAnd, bson.M{"address": bson.M{"$regex": kw, "$options": "i"}})
	}

	matchStage := bson.M{"cif": bson.M{"$nin": billsCifs}}
	if len(addressAnd) > 0 {
		matchStage["$and"] = addressAnd
	}

	pipeline := []bson.M{
		{"$match": matchStage},
		{"$group": bson.M{"_id": "$cif", "doc": bson.M{"$first": "$$ROOT"}}},
		{"$replaceRoot": bson.M{"newRoot": "$doc"}},
		{"$skip": page * size},
		{"$limit": size},
	}
	cur, err := s.repo.Collection().Aggregate(ctx, pipeline, options.Aggregate())
	if err != nil {
		return PageResult{}, err
	}
	defer func() { _ = cur.Close(ctx) }()
	var items []entity.Savings
	if err := cur.All(ctx, &items); err != nil {
		return PageResult{}, err
	}

	countPipeline := []bson.M{
		{"$match": matchStage},
		{"$group": bson.M{"_id": "$cif"}},
		{"$count": "total"},
	}
	countCur, err := s.repo.Collection().Aggregate(ctx, countPipeline, options.Aggregate())
	if err != nil {
		return PageResult{}, err
	}
	defer func() { _ = countCur.Close(ctx) }()
	var countRows []struct {
		Total int64 `bson:"total"`
	}
	if err := countCur.All(ctx, &countRows); err != nil {
		return PageResult{}, err
	}
	var total int64
	if len(countRows) > 0 {
		total = countRows[0].Total
	}

	return PageResult{Items: items, Total: total, Page: page, Size: size}, nil
}

// ParseCSVAndSave: hapus semua, lalu re-insert dari CSV. Header di-skip.
// Baris dengan kolom kurang dari 6 di-skip (data tidak lengkap).
func (s *Service) ParseCSVAndSave(ctx context.Context, path string, total int64, onProgress csvimport.ProgressFn) error {
	if err := s.repo.DeleteAll(ctx); err != nil {
		return err
	}
	if err := csvimport.Run(
		ctx, path, true, total,
		mapRow,
		s.repo.InsertMany,
		onProgress,
	); err != nil {
		return err
	}
	return s.updates.SaveUpdateTimestamp(ctx, "SAVING")
}

func mapRow(row []string) (entity.Savings, bool) {
	if len(row) < 6 {
		return entity.Savings{}, false
	}
	get := func(i int) string {
		if i < len(row) {
			return row[i]
		}
		return ""
	}
	parseLong := func(s string) int64 {
		s = strings.TrimSpace(s)
		if s == "" {
			return 0
		}
		n, _ := strconv.ParseInt(s, 10, 64)
		return n
	}
	return entity.Savings{
		Branch:          get(0),
		Type:            get(1),
		CIF:             get(2),
		TabID:           get(3),
		Name:            get(4),
		Address:         get(5),
		Balance:         parseLong(get(6)),
		Transaction:     parseLong(get(7)),
		AccountOfficer:  get(8),
		Phone:           get(9),
		MinimumBalance:  parseLong(get(10)),
		BlockingBalance: parseLong(get(11)),
	}, true
}
