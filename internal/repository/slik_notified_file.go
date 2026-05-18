package repository

import (
	"context"
	"errors"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"
	"go.mongodb.org/mongo-driver/v2/mongo/options"
)

type SlikNotifiedFileRepo struct{ coll *mongo.Collection }

func NewSlikNotifiedFileRepo(m *Mongo) *SlikNotifiedFileRepo {
	return &SlikNotifiedFileRepo{coll: m.DB.Collection("slik_notified_files")}
}

func (r *SlikNotifiedFileRepo) Exists(ctx context.Context, fileKey string) (bool, error) {
	err := r.coll.FindOne(ctx, bson.M{"_id": fileKey}, options.FindOne().SetProjection(bson.M{"_id": 1})).Err()
	if errors.Is(err, mongo.ErrNoDocuments) {
		return false, nil
	}
	if err != nil {
		return false, err
	}
	return true, nil
}

func (r *SlikNotifiedFileRepo) Save(ctx context.Context, f *entity.SlikNotifiedFile) error {
	opts := options.Replace().SetUpsert(true)
	_, err := r.coll.ReplaceOne(ctx, bson.M{"_id": f.FileKey}, f, opts)
	return err
}
