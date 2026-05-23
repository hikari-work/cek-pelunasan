package repository

import (
	"context"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"
	"go.mongodb.org/mongo-driver/v2/mongo/options"
)

type PaymentDetailsRepo struct{ coll *mongo.Collection }

func NewPaymentDetailsRepo(m *Mongo) *PaymentDetailsRepo {
	return &PaymentDetailsRepo{coll: m.DB.Collection("payment_details")}
}

func (r *PaymentDetailsRepo) UpsertMany(ctx context.Context, items []entity.PaymentDetails) error {
	for i := range items {
		if err := r.Save(ctx, &items[i]); err != nil {
			return err
		}
	}
	return nil
}

func (r *PaymentDetailsRepo) FindByKodeAO(ctx context.Context, kodeAO string, page Page) ([]entity.PaymentDetails, error) {
	return r.findPaged(ctx, bson.M{"kodeAo": kodeAO}, page, nil)
}

func (r *PaymentDetailsRepo) CountByKodeAO(ctx context.Context, kodeAO string) (int64, error) {
	return r.coll.CountDocuments(ctx, bson.M{"kodeAo": kodeAO})
}

func (r *PaymentDetailsRepo) FindByKodeCabangAndTanggal(ctx context.Context, kodeCabang, tanggal string, page Page) ([]entity.PaymentDetails, error) {
	return r.findPaged(ctx, bson.M{"kodeCabang": kodeCabang, "tanggal": tanggal}, page, nil)
}

func (r *PaymentDetailsRepo) CountByKodeCabangAndTanggal(ctx context.Context, kodeCabang, tanggal string) (int64, error) {
	return r.coll.CountDocuments(ctx, bson.M{"kodeCabang": kodeCabang, "tanggal": tanggal})
}

func (r *PaymentDetailsRepo) FindByTanggalAndFlagPelunasan(ctx context.Context, tanggal string, flag bool, page Page) ([]entity.PaymentDetails, error) {
	return r.findPaged(ctx, bson.M{"tanggal": tanggal, "flagPelunasan": flag}, page, nil)
}

func (r *PaymentDetailsRepo) CountByTanggalAndFlagPelunasan(ctx context.Context, tanggal string, flag bool) (int64, error) {
	return r.coll.CountDocuments(ctx, bson.M{"tanggal": tanggal, "flagPelunasan": flag})
}

func (r *PaymentDetailsRepo) FindByKodeAOAndTanggal(ctx context.Context, kodeAO, tanggal string) ([]entity.PaymentDetails, error) {
	cur, err := r.coll.Find(ctx, bson.M{"kodeAo": kodeAO, "tanggal": tanggal})
	if err != nil {
		return nil, err
	}
	defer deferCloseCursor(ctx, cur)()
	var out []entity.PaymentDetails
	if err := cur.All(ctx, &out); err != nil {
		return nil, err
	}
	return out, nil
}

func (r *PaymentDetailsRepo) FindByNoSpkOrdered(ctx context.Context, noSpk string) ([]entity.PaymentDetails, error) {
	opts := options.Find().SetSort(bson.D{
		{Key: "tanggal", Value: 1},
		{Key: "kodePosting", Value: 1},
	})
	cur, err := r.coll.Find(ctx, bson.M{"noSpk": noSpk}, opts)
	if err != nil {
		return nil, err
	}
	defer deferCloseCursor(ctx, cur)()
	var out []entity.PaymentDetails
	if err := cur.All(ctx, &out); err != nil {
		return nil, err
	}
	return out, nil
}

func (r *PaymentDetailsRepo) Save(ctx context.Context, p *entity.PaymentDetails) error {
	opts := options.Replace().SetUpsert(true)
	_, err := r.coll.ReplaceOne(ctx, bson.M{"_id": p.ID}, p, opts)
	return err
}

func (r *PaymentDetailsRepo) findPaged(ctx context.Context, filter bson.M, page Page, sort bson.D) ([]entity.PaymentDetails, error) {
	return findPaged[entity.PaymentDetails](ctx, r.coll, filter, page, sort)
}
