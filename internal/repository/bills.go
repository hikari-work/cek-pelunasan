package repository

import (
	"context"
	"errors"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"
	"go.mongodb.org/mongo-driver/v2/mongo/options"
)

type BillsRepo struct{ coll *mongo.Collection }

func NewBillsRepo(m *Mongo) *BillsRepo {
	return &BillsRepo{coll: m.DB.Collection("tagihan")}
}

func (r *BillsRepo) FindByID(ctx context.Context, noSpk string) (*entity.Bills, error) {
	var b entity.Bills
	err := r.coll.FindOne(ctx, bson.M{"_id": noSpk}).Decode(&b)
	if errors.Is(err, mongo.ErrNoDocuments) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &b, nil
}

func (r *BillsRepo) FindByAccountOfficerAndPayDown(ctx context.Context, ao, payDown string, page Page) ([]entity.Bills, error) {
	return r.findPaged(ctx, bson.M{"accountOfficer": ao, "payDown": payDown}, page, nil)
}

func (r *BillsRepo) CountByAccountOfficerAndPayDown(ctx context.Context, ao, payDown string) (int64, error) {
	return r.coll.CountDocuments(ctx, bson.M{"accountOfficer": ao, "payDown": payDown})
}

func (r *BillsRepo) FindByBranchAndPayDownOrderByAO(ctx context.Context, branch, payDown string, page Page) ([]entity.Bills, error) {
	return r.findPaged(ctx, bson.M{"branch": branch, "payDown": payDown}, page, bson.D{{Key: "accountOfficer", Value: 1}})
}

func (r *BillsRepo) CountByBranchAndPayDown(ctx context.Context, branch, payDown string) (int64, error) {
	return r.coll.CountDocuments(ctx, bson.M{"branch": branch, "payDown": payDown})
}

func (r *BillsRepo) FindByNameAndBranch(ctx context.Context, name, branch string, page Page) ([]entity.Bills, error) {
	filter := bson.M{
		"name":   bson.M{"$regex": name, "$options": "i"},
		"branch": branch,
	}
	return r.findPaged(ctx, filter, page, nil)
}

func (r *BillsRepo) CountByNameAndBranch(ctx context.Context, name, branch string) (int64, error) {
	filter := bson.M{
		"name":   bson.M{"$regex": name, "$options": "i"},
		"branch": branch,
	}
	return r.coll.CountDocuments(ctx, filter)
}

func (r *BillsRepo) FindByName(ctx context.Context, name string, page Page) ([]entity.Bills, error) {
	filter := bson.M{"name": bson.M{"$regex": name, "$options": "i"}}
	return r.findPaged(ctx, filter, page, nil)
}

func (r *BillsRepo) CountByName(ctx context.Context, name string) (int64, error) {
	return r.coll.CountDocuments(ctx, bson.M{"name": bson.M{"$regex": name, "$options": "i"}})
}

// FindByOfficeLocationWithMinTunggakan: tagihan kantor X dengan minInterest+minPrincipal > 0.
func (r *BillsRepo) FindByOfficeLocationWithMinTunggakan(ctx context.Context, officeLocation string, page Page) ([]entity.Bills, error) {
	filter := bson.M{
		"officeLocation": officeLocation,
		"$expr": bson.M{
			"$gt": bson.A{bson.M{"$add": bson.A{"$minInterest", "$minPrincipal"}}, 0},
		},
	}
	return r.findPaged(ctx, filter, page, nil)
}

func (r *BillsRepo) CountByOfficeLocationWithMinTunggakan(ctx context.Context, officeLocation string) (int64, error) {
	filter := bson.M{
		"officeLocation": officeLocation,
		"$expr": bson.M{
			"$gt": bson.A{bson.M{"$add": bson.A{"$minInterest", "$minPrincipal"}}, 0},
		},
	}
	return r.coll.CountDocuments(ctx, filter)
}

func (r *BillsRepo) FindByAOWithMinTunggakan(ctx context.Context, ao string, page Page) ([]entity.Bills, error) {
	filter := bson.M{
		"accountOfficer": ao,
		"$expr": bson.M{
			"$gt": bson.A{bson.M{"$add": bson.A{"$minInterest", "$minPrincipal"}}, 0},
		},
	}
	return r.findPaged(ctx, filter, page, nil)
}

func (r *BillsRepo) CountByAOWithMinTunggakan(ctx context.Context, ao string) (int64, error) {
	filter := bson.M{
		"accountOfficer": ao,
		"$expr": bson.M{
			"$gt": bson.A{bson.M{"$add": bson.A{"$minInterest", "$minPrincipal"}}, 0},
		},
	}
	return r.coll.CountDocuments(ctx, filter)
}

func (r *BillsRepo) FindByBranchAndKiosAndTotalMin(ctx context.Context, branch, kios string, minTotal int64, page Page) ([]entity.Bills, error) {
	filter := bson.M{
		"branch": branch,
		"kios":   kios,
		"$expr": bson.M{
			"$gt": bson.A{
				bson.M{"$add": bson.A{
					bson.M{"$ifNull": bson.A{"$minInterest", 0}},
					bson.M{"$ifNull": bson.A{"$minPrincipal", 0}},
				}},
				minTotal,
			},
		},
	}
	return r.findPaged(ctx, filter, page, nil)
}

func (r *BillsRepo) FindByBranchAndTotalMin(ctx context.Context, branch string, minTotal int64, page Page) ([]entity.Bills, error) {
	filter := bson.M{
		"branch": branch,
		"$expr": bson.M{
			"$gt": bson.A{
				bson.M{"$add": bson.A{
					bson.M{"$ifNull": bson.A{"$minInterest", 0}},
					bson.M{"$ifNull": bson.A{"$minPrincipal", 0}},
				}},
				minTotal,
			},
		},
	}
	return r.findPaged(ctx, filter, page, nil)
}

func (r *BillsRepo) FindByKiosAndTotalMin(ctx context.Context, kios string, minTotal int64, page Page) ([]entity.Bills, error) {
	return r.FindByBranchAndKiosAndTotalMin(ctx, "1075", kios, minTotal, page)
}

func (r *BillsRepo) FindAllByBranch(ctx context.Context, branch string) ([]entity.Bills, error) {
	cur, err := r.coll.Find(ctx, bson.M{"branch": branch})
	if err != nil {
		return nil, err
	}
	defer cur.Close(ctx)
	var out []entity.Bills
	if err := cur.All(ctx, &out); err != nil {
		return nil, err
	}
	return out, nil
}

func (r *BillsRepo) FindByBranchAndNoSpkNotIn(ctx context.Context, branch string, paidSpks []string) ([]entity.Bills, error) {
	filter := bson.M{"branch": branch, "_id": bson.M{"$nin": paidSpks}}
	cur, err := r.coll.Find(ctx, filter)
	if err != nil {
		return nil, err
	}
	defer cur.Close(ctx)
	var out []entity.Bills
	if err := cur.All(ctx, &out); err != nil {
		return nil, err
	}
	return out, nil
}

// FindByBranchAndMinInterestAndDayLateBetween adalah query untuk fitur /minbunga.
// branch + minInterest > 0 + collectStatus=02 + product != KUBTP + dayLate dalam [min, max).
func (r *BillsRepo) FindByBranchAndMinInterestAndDayLateBetween(ctx context.Context, branch string, minDayLate, maxDayLate int) ([]entity.Bills, error) {
	filter := minBungaFilter(bson.M{"branch": branch}, minDayLate, maxDayLate)
	cur, err := r.coll.Find(ctx, filter)
	if err != nil {
		return nil, err
	}
	defer cur.Close(ctx)
	var out []entity.Bills
	if err := cur.All(ctx, &out); err != nil {
		return nil, err
	}
	return out, nil
}

func (r *BillsRepo) FindByAOAndMinInterestAndDayLateBetween(ctx context.Context, ao string, minDayLate, maxDayLate int) ([]entity.Bills, error) {
	filter := minBungaFilter(bson.M{"accountOfficer": ao}, minDayLate, maxDayLate)
	cur, err := r.coll.Find(ctx, filter)
	if err != nil {
		return nil, err
	}
	defer cur.Close(ctx)
	var out []entity.Bills
	if err := cur.All(ctx, &out); err != nil {
		return nil, err
	}
	return out, nil
}

func minBungaFilter(base bson.M, minDayLate, maxDayLate int) bson.M {
	f := bson.M{}
	for k, v := range base {
		f[k] = v
	}
	f["minInterest"] = bson.M{"$gt": 0}
	f["collectStatus"] = "02"
	f["product"] = bson.M{"$ne": "KUBTP"}
	f["$expr"] = bson.M{
		"$and": bson.A{
			bson.M{"$gte": bson.A{
				bson.M{"$toInt": bson.M{"$ifNull": bson.A{"$dayLate", "0"}}},
				minDayLate,
			}},
			bson.M{"$lt": bson.A{
				bson.M{"$toInt": bson.M{"$ifNull": bson.A{"$dayLate", "0"}}},
				maxDayLate,
			}},
		},
	}
	return f
}

func (r *BillsRepo) findPaged(ctx context.Context, filter bson.M, page Page, sort bson.D) ([]entity.Bills, error) {
	opts := options.Find().SetSkip(page.Skip()).SetLimit(page.Limit())
	if sort != nil {
		opts.SetSort(sort)
	}
	cur, err := r.coll.Find(ctx, filter, opts)
	if err != nil {
		return nil, err
	}
	defer cur.Close(ctx)
	var out []entity.Bills
	if err := cur.All(ctx, &out); err != nil {
		return nil, err
	}
	return out, nil
}
