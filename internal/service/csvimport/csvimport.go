// Package csvimport berisi util import CSV bersama untuk service yang butuh
// pola yang sama: baca CSV (skip header optional), parse per baris, buffer
// per batch, callback progress yang di-throttle ke ~20 panggilan total.
package csvimport

import (
	"context"
	"encoding/csv"
	"errors"
	"io"
	"os"
	"sync/atomic"
)

// ProgressFn dipanggil setelah satu batch sukses disimpan. processed = total
// baris yang sudah masuk DB sampai saat ini. Boleh nil.
type ProgressFn func(processed int64)

// Run membaca file CSV, memetakan tiap baris pakai mapRow, buffer ke batch
// (size 500), lalu panggil saveBatch. Header di-skip kalau skipHeader true.
//
// total dipakai cuma untuk throttling progress. Boleh 0 (callback tetap jalan
// tiap batch tapi tanpa "5%-step" smoothing).
func Run[T any](
	ctx context.Context,
	path string,
	skipHeader bool,
	total int64,
	mapRow func([]string) (T, bool),
	saveBatch func(context.Context, []T) error,
	onProgress ProgressFn,
) error {
	f, err := os.Open(path)
	if err != nil {
		return err
	}
	defer func() { _ = f.Close() }()

	reader := csv.NewReader(f)
	reader.FieldsPerRecord = -1
	if skipHeader {
		if _, err := reader.Read(); err != nil && !errors.Is(err, io.EOF) {
			return err
		}
	}

	updateInterval := total / 20
	if updateInterval < 500 {
		updateInterval = 500
	}
	var processed atomic.Int64
	var lastStep atomic.Int64
	lastStep.Store(-1)

	const batchSize = 500
	batch := make([]T, 0, batchSize)
	flush := func() error {
		if len(batch) == 0 {
			return nil
		}
		if err := saveBatch(ctx, batch); err != nil {
			return err
		}
		done := processed.Add(int64(len(batch)))
		if onProgress != nil {
			step := done / updateInterval
			if step > lastStep.Swap(step) || done >= total {
				onProgress(done)
			}
		}
		batch = batch[:0]
		return nil
	}

	for {
		row, err := reader.Read()
		if errors.Is(err, io.EOF) {
			break
		}
		if err != nil {
			return err
		}
		v, ok := mapRow(row)
		if !ok {
			continue
		}
		batch = append(batch, v)
		if len(batch) >= batchSize {
			if err := flush(); err != nil {
				return err
			}
		}
	}
	return flush()
}
