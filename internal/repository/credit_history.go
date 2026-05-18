package repository

import (
	"context"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"
)

type CreditHistoryRepo struct{ coll *mongo.Collection }

func NewCreditHistoryRepo(m *Mongo) *CreditHistoryRepo {
	return &CreditHistoryRepo{coll: m.DB.Collection("credit_history")}
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
	defer cur.Close(ctx)
	var out []entity.CreditHistory
	if err := cur.All(ctx, &out); err != nil {
		return nil, err
	}
	return out, nil
}
