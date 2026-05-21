package repository

import (
	"context"
	"errors"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"
	"go.mongodb.org/mongo-driver/v2/mongo/options"
)

type PayingRepo struct{ coll *mongo.Collection }

func NewPayingRepo(m *Mongo) *PayingRepo {
	return &PayingRepo{coll: m.DB.Collection("paying")}
}

func (r *PayingRepo) FindByID(ctx context.Context, id string) (*entity.Paying, error) {
	var p entity.Paying
	err := r.coll.FindOne(ctx, bson.M{"_id": id}).Decode(&p)
	if errors.Is(err, mongo.ErrNoDocuments) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &p, nil
}

func (r *PayingRepo) Save(ctx context.Context, p *entity.Paying) error {
	opts := options.Replace().SetUpsert(true)
	_, err := r.coll.ReplaceOne(ctx, bson.M{"_id": p.ID}, p, opts)
	return err
}

func (r *PayingRepo) DeleteByID(ctx context.Context, id string) error {
	_, err := r.coll.DeleteOne(ctx, bson.M{"_id": id})
	return err
}

// DeleteAll hapus semua dokumen di koleksi paying. Dipakai oleh
// command admin .resetpaid untuk reset flag "sudah dibayar hari ini".
func (r *PayingRepo) DeleteAll(ctx context.Context) (int64, error) {
	res, err := r.coll.DeleteMany(ctx, bson.M{})
	if err != nil {
		return 0, err
	}
	return res.DeletedCount, nil
}

// FindAllIDs ambil semua _id koleksi paying — dipakai untuk filter "spk yang sudah lunas".
func (r *PayingRepo) FindAllIDs(ctx context.Context) ([]string, error) {
	cur, err := r.coll.Find(ctx, bson.M{}, options.Find().SetProjection(bson.M{"_id": 1}))
	if err != nil {
		return nil, err
	}
	defer cur.Close(ctx)
	var rows []struct {
		ID string `bson:"_id"`
	}
	if err := cur.All(ctx, &rows); err != nil {
		return nil, err
	}
	out := make([]string, 0, len(rows))
	for _, r := range rows {
		out = append(out, r.ID)
	}
	return out, nil
}
