// Package r2 membungkus minio-go untuk akses Cloudflare R2 (kompatibel S3).
//
// R2 mendukung path-style; region apa saja diterima, tapi endpoint harus eksplisit.
// Helpernya minimalis: GetObject (full bytes), ListObjectsByPrefix (semua key),
// dan PutObject untuk uploader.
package r2

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"io"
	"net/url"
	"strings"

	"github.com/minio/minio-go/v7"
	"github.com/minio/minio-go/v7/pkg/credentials"
)

// Client membungkus minio.Client + bucket bawaan.
type Client struct {
	mc     *minio.Client
	bucket string
}

// Config sesuai legacy. Endpoint boleh full URL (https://<account>.r2...) atau
// suffix saja — kalau suffix, dikonkatenasi dengan AccountID.
type Config struct {
	AccessKey string
	AccountID string
	SecretKey string
	Endpoint  string
	Bucket    string
}

// New bangun client. Mengembalikan client siap pakai walau credential kosong;
// di kasus itu request ke R2 akan fail saat dipakai (defer error sampai dipakai).
func New(cfg Config) (*Client, error) {
	if strings.TrimSpace(cfg.Bucket) == "" {
		return nil, errors.New("r2 bucket required")
	}
	endpoint := cfg.Endpoint
	if !strings.HasPrefix(endpoint, "http") {
		// suffix mode: "<account>.r2.cloudflarestorage.com"
		endpoint = "https://" + cfg.AccountID + "." + endpoint
	}
	u, err := url.Parse(endpoint)
	if err != nil {
		return nil, fmt.Errorf("parse r2 endpoint: %w", err)
	}
	secure := u.Scheme == "https"
	host := u.Host
	if host == "" {
		host = u.Path
	}

	mc, err := minio.New(host, &minio.Options{
		Creds:  credentials.NewStaticV4(cfg.AccessKey, cfg.SecretKey, ""),
		Secure: secure,
		Region: "us-east-1",
	})
	if err != nil {
		return nil, fmt.Errorf("init r2 client: %w", err)
	}
	return &Client{mc: mc, bucket: cfg.Bucket}, nil
}

// GetObject ambil 1 file utuh sebagai byte slice.
// Kembalikan (nil, nil) kalau object tidak ada — sesuai konvensi legacy
// yang pakai Mono.empty() di kasus 404.
func (c *Client) GetObject(ctx context.Context, key string) ([]byte, error) {
	obj, err := c.mc.GetObject(ctx, c.bucket, key, minio.GetObjectOptions{})
	if err != nil {
		return nil, err
	}
	defer obj.Close()

	var buf bytes.Buffer
	_, err = io.Copy(&buf, obj)
	if err != nil {
		if er, ok := errors.AsType[minio.ErrorResponse](err); ok && (er.Code == "NoSuchKey" || er.StatusCode == 404) {
			return nil, nil
		}
		// minio kadang return error generic; cek string.
		if strings.Contains(err.Error(), "key does not exist") || strings.Contains(err.Error(), "NoSuchKey") {
			return nil, nil
		}
		return nil, err
	}
	return buf.Bytes(), nil
}

// ListObjectsByPrefix list semua key dengan prefix tertentu (recursive).
// Pagination otomatis ditangani minio.ListObjects.
func (c *Client) ListObjectsByPrefix(ctx context.Context, prefix string) ([]string, error) {
	keys := make([]string, 0, 64)
	for obj := range c.mc.ListObjects(ctx, c.bucket, minio.ListObjectsOptions{
		Prefix:    prefix,
		Recursive: true,
	}) {
		if obj.Err != nil {
			return nil, obj.Err
		}
		keys = append(keys, obj.Key)
	}
	return keys, nil
}

// PutObject upload bytes ke key dengan content-type tertentu.
func (c *Client) PutObject(ctx context.Context, key string, data []byte, contentType string) error {
	_, err := c.mc.PutObject(ctx, c.bucket, key, bytes.NewReader(data), int64(len(data)), minio.PutObjectOptions{
		ContentType: contentType,
	})
	return err
}
