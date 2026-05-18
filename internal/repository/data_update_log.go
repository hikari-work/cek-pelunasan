package repository

import (
	"context"
	"errors"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"
	"go.mongodb.org/mongo-driver/v2/mongo/options"
)

type DataUpdateLogRepo struct{ coll *mongo.Collection }

func NewDataUpdateLogRepo(m *Mongo) *DataUpdateLogRepo {
	return &DataUpdateLogRepo{coll: m.DB.Collection("data_update_log")}
}

func (r *DataUpdateLogRepo) FindByID(ctx context.Context, dataType string) (*entity.DataUpdateLog, error) {
	var l entity.DataUpdateLog
	err := r.coll.FindOne(ctx, bson.M{"_id": dataType}).Decode(&l)
	if errors.Is(err, mongo.ErrNoDocuments) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &l, nil
}

func (r *DataUpdateLogRepo) Save(ctx context.Context, l *entity.DataUpdateLog) error {
	opts := options.Replace().SetUpsert(true)
	_, err := r.coll.ReplaceOne(ctx, bson.M{"_id": l.DataType}, l, opts)
	return err
}
