// Package minbunga: kalkulator + session untuk fitur /minbunga.
// Calculator pure logic; SessionService menyimpan state pemilihan tanggal di Mongo.
package minbunga

import (
	"context"
	"sort"
	"strconv"
	"strings"
	"time"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
	"github.com/hikari-work/cek-pelunasan/internal/repository"
)

var jakartaTZ = time.FixedZone("WIB", 7*3600)

// DatedBill = Bills + dayLate yang sudah ter-parse jadi int.
type DatedBill struct {
	Bill    entity.Bills
	DayLate int
}

func toDated(b entity.Bills) DatedBill {
	return DatedBill{Bill: b, DayLate: parseDayLate(b.DayLate)}
}

// BillsForDate: kelompok tagihan yang akan tembus hari ke-90 di tanggal targetDate.
type BillsForDate struct {
	TargetDate time.Time
	DaysDiff   int
	Bills      []DatedBill
}

// Calculate menghitung kelompok tagihan per tanggal target.
//
// Aturan: tagihan masuk grup tanggal X kalau dayLate + (X - today) >= 90.
// Tagihan dengan SPK yang sudah muncul di tanggal sebelumnya (urut menaik)
// tidak ditampilkan ulang.
func Calculate(allBills []entity.Bills, targets []time.Time) []BillsForDate {
	today := time.Now().In(jakartaTZ).Truncate(24 * time.Hour)
	dated := make([]DatedBill, 0, len(allBills))
	for _, b := range allBills {
		dated = append(dated, toDated(b))
	}

	sorted := make([]time.Time, len(targets))
	copy(sorted, targets)
	sort.Slice(sorted, func(i, j int) bool { return sorted[i].Before(sorted[j]) })

	shown := make(map[string]struct{})
	out := make([]BillsForDate, 0, len(sorted))
	for _, date := range sorted {
		daysDiff := int(date.Sub(today).Hours() / 24)
		var forDate []DatedBill
		for _, db := range dated {
			if db.DayLate+daysDiff < 90 {
				continue
			}
			if _, seen := shown[db.Bill.NoSpk]; seen {
				continue
			}
			forDate = append(forDate, db)
		}
		if len(forDate) == 0 {
			continue
		}
		for _, db := range forDate {
			shown[db.Bill.NoSpk] = struct{}{}
		}
		out = append(out, BillsForDate{TargetDate: date, DaysDiff: daysDiff, Bills: forDate})
	}
	return out
}

// MinDayLateThreshold: minimum dayLate yang masih mungkin tembus 90 hari di salah
// satu target. Bills dengan dayLate di bawah threshold ini tidak perlu di-load.
func MinDayLateThreshold(targets []time.Time) int {
	today := time.Now().In(jakartaTZ).Truncate(24 * time.Hour)
	maxDiff := 0
	for _, d := range targets {
		diff := int(d.Sub(today).Hours() / 24)
		if diff > maxDiff {
			maxDiff = diff
		}
	}
	threshold := 90 - maxDiff
	if threshold < 0 {
		threshold = 0
	}
	return threshold
}

func parseDayLate(s string) int {
	s = strings.TrimSpace(s)
	if s == "" {
		return 0
	}
	n, err := strconv.Atoi(s)
	if err != nil {
		return 0
	}
	return n
}

// SessionService menyimpan state UI pemilihan tanggal /minbunga di Mongo.
// Dokumen punya TTL 30 menit (lihat entity.MinBungaSessionTTL + index Mongo).
type SessionService struct {
	repo *repository.MinBungaSessionRepo
}

func NewSessionService(repo *repository.MinBungaSessionRepo) *SessionService {
	return &SessionService{repo: repo}
}

func (s *SessionService) GetOrCreate(ctx context.Context, chatID int64, identifier, role string) (*entity.MinBungaSession, error) {
	key := strconv.FormatInt(chatID, 10)
	now := time.Now().In(jakartaTZ)
	existing, err := s.repo.FindByID(ctx, key)
	if err != nil {
		return nil, err
	}
	if existing != nil {
		existing.Identifier = identifier
		existing.Role = role
		existing.SelectedDates = nil
		existing.MessageID = 0
		existing.CreatedAt = now
		if err := s.repo.Save(ctx, existing); err != nil {
			return nil, err
		}
		return existing, nil
	}
	fresh := &entity.MinBungaSession{
		ChatID:     key,
		Identifier: identifier,
		Role:       role,
		CreatedAt:  now,
	}
	if err := s.repo.Save(ctx, fresh); err != nil {
		return nil, err
	}
	return fresh, nil
}

func (s *SessionService) SetMessageID(ctx context.Context, chatID, messageID int64) (*entity.MinBungaSession, error) {
	return s.update(ctx, chatID, func(sess *entity.MinBungaSession) {
		sess.MessageID = messageID
	})
}

func (s *SessionService) ToggleDate(ctx context.Context, chatID int64, date string) (*entity.MinBungaSession, error) {
	return s.update(ctx, chatID, func(sess *entity.MinBungaSession) {
		for i, d := range sess.SelectedDates {
			if d == date {
				sess.SelectedDates = append(sess.SelectedDates[:i], sess.SelectedDates[i+1:]...)
				return
			}
		}
		sess.SelectedDates = append(sess.SelectedDates, date)
	})
}

func (s *SessionService) ClearDates(ctx context.Context, chatID int64) (*entity.MinBungaSession, error) {
	return s.update(ctx, chatID, func(sess *entity.MinBungaSession) {
		sess.SelectedDates = nil
	})
}

func (s *SessionService) Get(ctx context.Context, chatID int64) (*entity.MinBungaSession, error) {
	return s.repo.FindByID(ctx, strconv.FormatInt(chatID, 10))
}

func (s *SessionService) Delete(ctx context.Context, chatID int64) error {
	return s.repo.DeleteByID(ctx, strconv.FormatInt(chatID, 10))
}

func (s *SessionService) update(ctx context.Context, chatID int64, fn func(*entity.MinBungaSession)) (*entity.MinBungaSession, error) {
	sess, err := s.Get(ctx, chatID)
	if err != nil || sess == nil {
		return sess, err
	}
	fn(sess)
	if err := s.repo.Save(ctx, sess); err != nil {
		return nil, err
	}
	return sess, nil
}
