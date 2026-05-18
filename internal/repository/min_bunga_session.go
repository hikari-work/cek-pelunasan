package repository

import (
	"context"
	"errors"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"
	"go.mongodb.org/mongo-driver/v2/mongo/options"
)

type MinBungaSessionRepo struct{ coll *mongo.Collection }

func NewMinBungaSessionRepo(m *Mongo) *MinBungaSessionRepo {
	return &MinBungaSessionRepo{coll: m.DB.Collection("min_bunga_session")}
}

func (r *MinBungaSessionRepo) FindByID(ctx context.Context, chatID string) (*entity.MinBungaSession, error) {
	var s entity.MinBungaSession
	err := r.coll.FindOne(ctx, bson.M{"_id": chatID}).Decode(&s)
	if errors.Is(err, mongo.ErrNoDocuments) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &s, nil
}

func (r *MinBungaSessionRepo) Save(ctx context.Context, s *entity.MinBungaSession) error {
	opts := options.Replace().SetUpsert(true)
	_, err := r.coll.ReplaceOne(ctx, bson.M{"_id": s.ChatID}, s, opts)
	return err
}

func (r *MinBungaSessionRepo) DeleteByID(ctx context.Context, chatID string) error {
	_, err := r.coll.DeleteOne(ctx, bson.M{"_id": chatID})
	return err
}
