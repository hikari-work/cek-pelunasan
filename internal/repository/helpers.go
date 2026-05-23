package repository

import (
	"context"
	"log/slog"

	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"
	"go.mongodb.org/mongo-driver/v2/mongo/options"
)

// deferCloseCursor returns a function that closes the cursor and logs any error.
// Usage defer deferCloseCursor(ctx, cur)()
func deferCloseCursor(ctx context.Context, cur *mongo.Cursor) func() {
	return func() {
		if err := cur.Close(ctx); err != nil {
			slog.Error("failed to close cursor", "error", err)
		}
	}
}

// findPaged executes a paginated MongoDB find query with optional sorting.
// This generic helper eliminates code duplication across repository methods.
//
// Parameters:
//   - ctx: context for the query
//   - coll: MongoDB collection to query
//   - filter: BSON filter for the query
//   - page: pagination parameters (skip/limit)
//   - sort: optional sort order (pass nil to skip sorting)
//
// Returns:
//   - []T: slice of results of type T
//   - error: any error that occurred during the query
func findPaged[T any](ctx context.Context, coll *mongo.Collection, filter bson.M, page Page, sort bson.D) ([]T, error) {
	opts := options.Find()
	if !page.Unlimited() {
		opts.SetSkip(page.Skip()).SetLimit(page.Limit())
	}
	if sort != nil {
		opts.SetSort(sort)
	}

	cur, err := coll.Find(ctx, filter, opts)
	if err != nil {
		return nil, err
	}
	defer deferCloseCursor(ctx, cur)()

	var out []T
	if err := cur.All(ctx, &out); err != nil {
		return nil, err
	}
	return out, nil
}
