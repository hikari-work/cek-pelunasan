// Package repository menyediakan akses ke MongoDB untuk seluruh koleksi.
// Setiap file repository = satu koleksi, mengikuti struktur Spring Data lama
// supaya migrasi service layer bisa dilakukan satu modul per satu modul.
package repository

import (
	"context"
	"fmt"
	"time"

	"go.mongodb.org/mongo-driver/v2/mongo"
	"go.mongodb.org/mongo-driver/v2/mongo/options"
)

// Mongo membungkus koneksi yang sudah teruji (Ping berhasil) sehingga
// service tidak perlu memanggil Ping berulang kali.
type Mongo struct {
	Client *mongo.Client
	DB     *mongo.Database
}

// Connect membuka koneksi ke MongoDB lalu memverifikasi via Ping.
// Ping di-bound 5 detik agar startup tidak menggantung kalau URI salah.
//
// BSONOptions.ObjectIDAsHexString diaktifkan supaya dokumen lama yang
// _id-nya bertipe ObjectID (mis. legacy Java auto-generate) bisa
// di-decode ke field Go bertipe string. Tanpa flag ini, decoder
// mongo-driver/v2 akan return error.
//
// Connection pool settings disesuaikan untuk server dengan RAM terbatas (~1GB):
// - MaxPoolSize=40: maksimal 40 koneksi concurrent (~40-80MB overhead)
// - MinPoolSize=5: idle pool kecil untuk mengurangi memory footprint
// - MaxConnIdleTime=30s: tutup idle connection cepat untuk free memory
func Connect(ctx context.Context, uri string) (*Mongo, error) {
	clientOpts := options.Client().
		ApplyURI(uri).
		SetMaxPoolSize(40).
		SetMinPoolSize(5).
		SetMaxConnIdleTime(30 * time.Second).
		SetBSONOptions(&options.BSONOptions{
			ObjectIDAsHexString: true,
		})
	client, err := mongo.Connect(clientOpts)
	if err != nil {
		return nil, fmt.Errorf("mongo connect: %w", err)
	}

	pingCtx, cancel := context.WithTimeout(ctx, 5*time.Second)
	defer cancel()
	if err := client.Ping(pingCtx, nil); err != nil {
		_ = client.Disconnect(context.Background())
		return nil, fmt.Errorf("mongo ping: %w", err)
	}

	dbName := dbNameFromURI(uri)
	if dbName == "" {
		dbName = "cek_pelunasan"
	}

	return &Mongo{
		Client: client,
		DB:     client.Database(dbName),
	}, nil
}

func (m *Mongo) Close(ctx context.Context) error {
	return m.Client.Disconnect(ctx)
}

// dbNameFromURI mengekstrak nama database dari URI MongoDB.
// Ex "mongodb://host:27017/cek_pelunasan?opts=..." -> "cek_pelunasan".
func dbNameFromURI(uri string) string {
	// Cari "/" setelah host, ambil sampai "?".
	i := -1
	slashCount := 0
	for idx, c := range uri {
		if c == '/' {
			slashCount++
			if slashCount == 3 {
				i = idx + 1
				break
			}
		}
	}
	if i < 0 || i >= len(uri) {
		return ""
	}
	end := len(uri)
	for j := i; j < len(uri); j++ {
		if uri[j] == '?' {
			end = j
			break
		}
	}
	return uri[i:end]
}
