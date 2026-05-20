package repository

import (
	"context"
	"errors"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"
	"go.mongodb.org/mongo-driver/v2/mongo/options"
)

type SavingsRepo struct{ coll *mongo.Collection }

func NewSavingsRepo(m *Mongo) *SavingsRepo {
	return &SavingsRepo{coll: m.DB.Collection("savings")}
}

func (r *SavingsRepo) Collection() *mongo.Collection {
	return r.coll
}

func (r *SavingsRepo) DeleteAll(ctx context.Context) error {
	_, err := r.coll.DeleteMany(ctx, bson.M{})
	return err
}

func (r *SavingsRepo) InsertMany(ctx context.Context, items []entity.Savings) error {
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

func (r *SavingsRepo) Count(ctx context.Context) (int64, error) {
	return r.coll.CountDocuments(ctx, bson.M{})
}

func (r *SavingsRepo) FindByNameAndBranch(ctx context.Context, name, branch string, page Page) ([]entity.Savings, error) {
	filter := bson.M{
		"name":   bson.M{"$regex": name, "$options": "i"},
		"branch": branch,
	}
	opts := options.Find()
	if !page.Unlimited() {
		opts.SetSkip(page.Skip()).SetLimit(page.Limit())
	}
	cur, err := r.coll.Find(ctx, filter, opts)
	if err != nil {
		return nil, err
	}
	defer cur.Close(ctx)
	var out []entity.Savings
	if err := cur.All(ctx, &out); err != nil {
		return nil, err
	}
	return out, nil
}

func (r *SavingsRepo) CountByNameAndBranch(ctx context.Context, name, branch string) (int64, error) {
	filter := bson.M{
		"name":   bson.M{"$regex": name, "$options": "i"},
		"branch": branch,
	}
	return r.coll.CountDocuments(ctx, filter)
}

func (r *SavingsRepo) FindByTabID(ctx context.Context, tabID string) (*entity.Savings, error) {
	var s entity.Savings
	err := r.coll.FindOne(ctx, bson.M{"tabId": tabID}).Decode(&s)
	if errors.Is(err, mongo.ErrNoDocuments) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &s, nil
}

func (r *SavingsRepo) FindByName(ctx context.Context, name string) ([]entity.Savings, error) {
	cur, err := r.coll.Find(ctx, bson.M{"name": bson.M{"$regex": name, "$options": "i"}})
	if err != nil {
		return nil, err
	}
	defer cur.Close(ctx)
	var out []entity.Savings
	if err := cur.All(ctx, &out); err != nil {
		return nil, err
	}
	return out, nil
}

func (r *SavingsRepo) FindByCIF(ctx context.Context, cif string) (*entity.Savings, error) {
	var s entity.Savings
	err := r.coll.FindOne(ctx, bson.M{"cif": cif}).Decode(&s)
	if errors.Is(err, mongo.ErrNoDocuments) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &s, nil
}
