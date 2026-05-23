package repository

import (
	"context"
	"errors"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"
	"go.mongodb.org/mongo-driver/v2/mongo/options"
)

type KolekTasRepo struct{ coll *mongo.Collection }

func NewKolekTasRepo(m *Mongo) *KolekTasRepo {
	return &KolekTasRepo{coll: m.DB.Collection("kolek_tas")}
}

func (r *KolekTasRepo) DeleteAll(ctx context.Context) error {
	_, err := r.coll.DeleteMany(ctx, bson.M{})
	return err
}

func (r *KolekTasRepo) InsertMany(ctx context.Context, items []entity.KolekTas) error {
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

func (r *KolekTasRepo) FindByKelompok(ctx context.Context, kelompok string, page Page) ([]entity.KolekTas, error) {
	filter := bson.M{"kelompok": bson.M{"$regex": "^" + kelompok + "$", "$options": "i"}}
	opts := options.Find()
	if !page.Unlimited() {
		opts.SetSkip(page.Skip()).SetLimit(page.Limit())
	}
	cur, err := r.coll.Find(ctx, filter, opts)
	if err != nil {
		return nil, err
	}
	defer cur.Close(ctx)
	var out []entity.KolekTas
	if err := cur.All(ctx, &out); err != nil {
		return nil, err
	}
	return out, nil
}

func (r *KolekTasRepo) FindAllByKelompok(ctx context.Context, kelompok string) ([]entity.KolekTas, error) {
	filter := bson.M{"kelompok": bson.M{"$regex": "^" + kelompok + "$", "$options": "i"}}
	cur, err := r.coll.Find(ctx, filter)
	if err != nil {
		return nil, err
	}
	defer cur.Close(ctx)
	var out []entity.KolekTas
	if err := cur.All(ctx, &out); err != nil {
		return nil, err
	}
	return out, nil
}

func (r *KolekTasRepo) CountByKelompok(ctx context.Context, kelompok string) (int64, error) {
	filter := bson.M{"kelompok": bson.M{"$regex": "^" + kelompok + "$", "$options": "i"}}
	return r.coll.CountDocuments(ctx, filter)
}

func (r *KolekTasRepo) FindByID(ctx context.Context, id string) (*entity.KolekTas, error) {
	var result entity.KolekTas
	err := r.coll.FindOne(ctx, bson.M{"_id": id}).Decode(&result)
	if errors.Is(err, mongo.ErrNoDocuments) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &result, nil
}
