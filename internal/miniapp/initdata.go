package miniapp

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"net/url"
	"sort"
	"strconv"
	"strings"
	"time"
)

// initDataVerifier memvalidasi initData dari Telegram Mini App pakai HMAC-SHA256.
// Token disimpan di field unexported supaya tidak ikut di-log.
type initDataVerifier struct {
	botToken string
}

func newInitDataVerifier(token string) *initDataVerifier {
	return &initDataVerifier{botToken: token}
}

type verifyResult struct {
	Valid     bool
	ChatID    int64
	FirstName string
}

const initDataMaxAge = 24 * time.Hour

// Verify mengembalikan {Valid: true} kalau hash cocok dan auth_date masih
// dalam jendela 24 jam.
func (v *initDataVerifier) Verify(initData string) verifyResult {
	if strings.TrimSpace(initData) == "" {
		return verifyResult{}
	}

	decoded, err := url.QueryUnescape(initData)
	if err != nil {
		return verifyResult{}
	}

	pairs := strings.Split(decoded, "&")
	params := make(map[string]string, len(pairs))
	for _, p := range pairs {
		eq := strings.IndexByte(p, '=')
		if eq < 0 {
			continue
		}
		params[p[:eq]] = p[eq+1:]
	}

	receivedHash, ok := params["hash"]
	if !ok {
		return verifyResult{}
	}
	delete(params, "hash")

	if authDate, ok := params["auth_date"]; ok {
		ts, err := strconv.ParseInt(authDate, 10, 64)
		if err != nil || time.Since(time.Unix(ts, 0)) > initDataMaxAge {
			return verifyResult{}
		}
	}

	keys := make([]string, 0, len(params))
	for k := range params {
		keys = append(keys, k)
	}
	sort.Strings(keys)

	var sb strings.Builder
	for i, k := range keys {
		if i > 0 {
			sb.WriteByte('\n')
		}
		sb.WriteString(k)
		sb.WriteByte('=')
		sb.WriteString(params[k])
	}

	// secretKey = HMAC_SHA256("WebAppData", botToken)
	secretMac := hmac.New(sha256.New, []byte("WebAppData"))
	secretMac.Write([]byte(v.botToken))
	secret := secretMac.Sum(nil)

	mac := hmac.New(sha256.New, secret)
	mac.Write([]byte(sb.String()))
	expected := hex.EncodeToString(mac.Sum(nil))

	if !strings.EqualFold(expected, receivedHash) {
		return verifyResult{}
	}

	userJSON, ok := params["user"]
	if !ok {
		return verifyResult{}
	}
	var user struct {
		ID        int64  `json:"id"`
		FirstName string `json:"first_name"`
	}
	if err := json.Unmarshal([]byte(userJSON), &user); err != nil {
		return verifyResult{}
	}
	if user.ID == 0 {
		return verifyResult{}
	}
	return verifyResult{Valid: true, ChatID: user.ID, FirstName: user.FirstName}
}

func (verifyResult) String() string { return fmt.Sprintf("verify-result") }
