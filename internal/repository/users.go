package repository

import (
	"context"
	"errors"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"
	"go.mongodb.org/mongo-driver/v2/mongo/options"
)

type UserRepo struct{ coll *mongo.Collection }

func NewUserRepo(m *Mongo) *UserRepo {
	return &UserRepo{coll: m.DB.Collection("users")}
}

func (r *UserRepo) FindByID(ctx context.Context, chatID int64) (*entity.User, error) {
	var u entity.User
	err := r.coll.FindOne(ctx, bson.M{"_id": chatID}).Decode(&u)
	if errors.Is(err, mongo.ErrNoDocuments) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &u, nil
}

func (r *UserRepo) FindByUserCode(ctx context.Context, code string) ([]entity.User, error) {
	cur, err := r.coll.Find(ctx, bson.M{"userCode": code})
	if err != nil {
		return nil, err
	}
	defer cur.Close(ctx)
	var out []entity.User
	if err := cur.All(ctx, &out); err != nil {
		return nil, err
	}
	return out, nil
}

func (r *UserRepo) Save(ctx context.Context, u *entity.User) error {
	opts := options.Replace().SetUpsert(true)
	_, err := r.coll.ReplaceOne(ctx, bson.M{"_id": u.ChatID}, u, opts)
	return err
}

func (r *UserRepo) DeleteByID(ctx context.Context, chatID int64) error {
	_, err := r.coll.DeleteOne(ctx, bson.M{"_id": chatID})
	return err
}

func (r *UserRepo) FindAll(ctx context.Context) ([]entity.User, error) {
	cur, err := r.coll.Find(ctx, bson.M{})
	if err != nil {
		return nil, err
	}
	defer cur.Close(ctx)
	var out []entity.User
	if err := cur.All(ctx, &out); err != nil {
		return nil, err
	}
	return out, nil
}
