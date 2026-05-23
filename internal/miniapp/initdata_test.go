package miniapp

import (
	"net/url"
	"strconv"
	"testing"
	"time"
)

// buildSignedInitData membentuk initData mock yang valid untuk botToken yang diberikan.
// Output sudah URL-encoded; verifier akan men-decode kembali sebelum cek hash.
func buildSignedInitData(t *testing.T, botToken string, fields map[string]string) string {
	t.Helper()
	dataCheckString := buildDataCheckString(fields)
	hash := computeHash(botToken, dataCheckString)

	values := url.Values{}
	for k, v := range fields {
		values.Set(k, v)
	}
	values.Set("hash", hash)
	return values.Encode()
}

func TestVerify_ValidInitData(t *testing.T) {
	v := newInitDataVerifier("test-bot-token")
	now := strconv.FormatInt(time.Now().Unix(), 10)
	initData := buildSignedInitData(t, "test-bot-token", map[string]string{
		"auth_date": now,
		"user":      `{"id":12345,"first_name":"Stef"}`,
	})

	got := v.Verify(initData)
	if !got.Valid {
		t.Fatalf("expected valid, got invalid")
	}
	if got.ChatID != 12345 {
		t.Errorf("ChatID = %d, want 12345", got.ChatID)
	}
	if got.FirstName != "Stef" {
		t.Errorf("FirstName = %q, want Stef", got.FirstName)
	}
}

func TestVerify_RejectsBadHash(t *testing.T) {
	v := newInitDataVerifier("test-bot-token")
	bad := "auth_date=" + strconv.FormatInt(time.Now().Unix(), 10) +
		"&user=%7B%22id%22%3A1%7D&hash=deadbeef"
	if got := v.Verify(bad); got.Valid {
		t.Error("expected invalid for bad hash")
	}
}

func TestVerify_RejectsExpired(t *testing.T) {
	v := newInitDataVerifier("test-bot-token")
	old := strconv.FormatInt(time.Now().Add(-25*time.Hour).Unix(), 10)
	initData := buildSignedInitData(t, "test-bot-token", map[string]string{
		"auth_date": old,
		"user":      `{"id":1,"first_name":"X"}`,
	})
	if got := v.Verify(initData); got.Valid {
		t.Error("expected invalid for expired auth_date")
	}
}

func TestVerify_RejectsEmpty(t *testing.T) {
	v := newInitDataVerifier("test-bot-token")
	if got := v.Verify(""); got.Valid {
		t.Error("expected invalid for empty initData")
	}
}
