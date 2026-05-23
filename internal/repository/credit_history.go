package repository

import (
	"context"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"
	"go.mongodb.org/mongo-driver/v2/mongo/options"
)

type CreditHistoryRepo struct{ coll *mongo.Collection }

func NewCreditHistoryRepo(m *Mongo) *CreditHistoryRepo {
	return &CreditHistoryRepo{coll: m.DB.Collection("credit_history")}
}

func (r *CreditHistoryRepo) Collection() *mongo.Collection {
	return r.coll
}

func (r *CreditHistoryRepo) DeleteAll(ctx context.Context) error {
	_, err := r.coll.DeleteMany(ctx, bson.M{})
	return err
}

func (r *CreditHistoryRepo) InsertMany(ctx context.Context, items []entity.CreditHistory) error {
	if len(items) == 0 {
		return nil
	}
	docs := make([]any, len(items))
	for i := range items {
		docs[i] = items[i]
	}
	opts := options.InsertMany().SetOrdered(false)
	_, err := r.coll.InsertMany(ctx, docs, opts)
	if isDuplicateKeyError(err) {
		return nil
	}
	return err
}

func (r *CreditHistoryRepo) Count(ctx context.Context) (int64, error) {
	return r.coll.CountDocuments(ctx, bson.M{})
}

func (r *CreditHistoryRepo) Save(ctx context.Context, h *entity.CreditHistory) error {
	if h.ID == "" {
		_, err := r.coll.InsertOne(ctx, h)
		return err
	}
	_, err := r.coll.ReplaceOne(ctx, bson.M{"_id": h.ID}, h)
	return err
}

func (r *CreditHistoryRepo) FindByStatus(ctx context.Context, status string) ([]entity.CreditHistory, error) {
	filter := bson.M{"status": bson.M{"$regex": "^" + status + "$", "$options": "i"}}
	cur, err := r.coll.Find(ctx, filter)
	if err != nil {
		return nil, err
	}
	defer deferCloseCursor(ctx, cur)()
	var out []entity.CreditHistory
	if err := cur.All(ctx, &out); err != nil {
		return nil, err
	}
	return out, nil
}
