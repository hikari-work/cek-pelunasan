package repository

import (
	"errors"

	"go.mongodb.org/mongo-driver/v2/mongo"
)

// isDuplicateKeyError true kalau err dari InsertMany cuma berisi duplicate
// key error (E11000) — bukan kombinasi dengan error lain. Caller boleh
// treat sebagai sukses.
//
// Logika pakai mongo.ServerError.HasErrorCode(11000), lalu pastikan
// SEMUA write error juga code 11000. Kalau ada error code lain (mis. 121
// document validation), tetap return false supaya caller tidak swallow
// error real.
func isDuplicateKeyError(err error) bool {
	if err == nil {
		return false
	}
	if bwe, ok := errors.AsType[mongo.BulkWriteException](err); ok {
		if len(bwe.WriteErrors) == 0 {
			return false
		}
		for _, we := range bwe.WriteErrors {
			if we.Code != 11000 {
				return false
			}
		}
		return true
	}
	if we, ok := errors.AsType[mongo.WriteException](err); ok {
		if len(we.WriteErrors) == 0 {
			return false
		}
		for _, e := range we.WriteErrors {
			if e.Code != 11000 {
				return false
			}
		}
		return true
	}
	return false
}
