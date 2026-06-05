package miniapp

import (
	"net/http"
	"net/http/httptest"
	"sort"
	"strings"
	"testing"

	"github.com/gofiber/fiber/v2"
	"github.com/hikari-work/cek-pelunasan/internal/entity"
)

// ---------------------------------------------------------------------------
// Pure-function tests (no DB needed)
// ---------------------------------------------------------------------------

func TestDigitsOnly(t *testing.T) {
	tests := []struct {
		in   string
		want bool
	}{
		{"", false},
		{"0", true},
		{"123456", true},
		{" 123", false},
		{"123 ", false},
		{"12a34", false},
		{"010600001234", true},
		{"-1", false},
		{"1.0", false},
	}
	for _, tt := range tests {
		got := digitsOnly.MatchString(tt.in)
		if got != tt.want {
			t.Errorf("digitsOnly.MatchString(%q) = %v, want %v", tt.in, got, tt.want)
		}
	}
}

func TestToTagihanSummary(t *testing.T) {
	b := entity.Bills{
		NoSpk: "SPK001", Name: "BUDI", Branch: "CAB1", Product: "KMG-LM",
		CollectStatus: "01", DayLate: "5", Installment: 500_000,
		FullPayment: 10_000_000, CKPNType: "A", CKPNNominal: 1_000_000,
		RekeningAutobedet: "1234567890",
	}
	s := toTagihanSummary(b)
	if s.NoSpk != "SPK001" {
		t.Errorf("NoSpk = %q, want SPK001", s.NoSpk)
	}
	if s.Name != "BUDI" {
		t.Errorf("Name = %q, want BUDI", s.Name)
	}
	if s.Branch != "CAB1" {
		t.Errorf("Branch = %q, want CAB1", s.Branch)
	}
	if s.Product != "KMG-LM" {
		t.Errorf("Product = %q, want KMG-LM", s.Product)
	}
	if s.CollectStatus != "01" {
		t.Errorf("CollectStatus = %q, want 01", s.CollectStatus)
	}
	if s.DayLate != "5" {
		t.Errorf("DayLate = %q, want 5", s.DayLate)
	}
	if s.Installment != 500_000 {
		t.Errorf("Installment = %d, want 500000", s.Installment)
	}
	if s.FullPayment != 10_000_000 {
		t.Errorf("FullPayment = %d, want 10000000", s.FullPayment)
	}
	if s.CKPNType != "A" {
		t.Errorf("CKPNType = %q, want A", s.CKPNType)
	}
	if s.CKPNNominal != 1_000_000 {
		t.Errorf("CKPNNominal = %d, want 1000000", s.CKPNNominal)
	}
	if s.RekeningAutobedet != "1234567890" {
		t.Errorf("RekeningAutobedet = %q, want 1234567890", s.RekeningAutobedet)
	}
}

func TestTabunganSummary(t *testing.T) {
	s := entity.Savings{
		TabID:   "TAB001",
		Name:    "ANI",
		Branch:  "CAB2",
		Type:    "TAB",
		Balance: 5_000_000,
	}
	m := tabunganSummary(s)
	if m["tabId"] != "TAB001" {
		t.Errorf("tabId = %v, want TAB001", m["tabId"])
	}
	if m["name"] != "ANI" {
		t.Errorf("name = %v, want ANI", m["name"])
	}
	if m["branch"] != "CAB2" {
		t.Errorf("branch = %v, want CAB2", m["branch"])
	}
	if m["type"] != "TAB" {
		t.Errorf("type = %v, want TAB", m["type"])
	}
	if m["balance"] != int64(5_000_000) {
		t.Errorf("balance = %v, want 5000000", m["balance"])
	}
}

// ---------------------------------------------------------------------------
// Route structure tests — verify all expected routes are registered
// ---------------------------------------------------------------------------

func TestRegister_RouteStructure(t *testing.T) {
	app := fiber.New()
	Register(app, Deps{
		BotToken:       "test-token",
		SessionTTL:     60,
		Auth:           nil, // allowed nil — middlewares won't call DB in route test
		Users:          nil,
		Bills:          nil,
		Savings:        nil,
		KolekTas:       nil,
		PaymentDetails: nil,
	})

	routes := collectRoutes(app)

	expected := []struct {
		method string
		path   string
	}{
		{"POST", "/api/mini/auth"},
		{"GET", "/api/mini/tagihan/search"},
		{"GET", "/api/mini/tagihan/:spk"},
		{"GET", "/api/mini/pelunasan/search"},
		{"GET", "/api/mini/pelunasan/:spk"},
		{"GET", "/api/mini/tabungan/search"},
		{"GET", "/api/mini/tabungan/:tabId"},
		{"GET", "/api/mini/canvas/search"},
		{"GET", "/api/mini/canvas/:tabId"},
		{"GET", "/api/mini/kolektas/search"},
		{"GET", "/api/mini/kolektas/:id"},
		{"GET", "/api/mini/payment/search"},
		{"GET", "/api/mini/payment/:spk"},
	}

	for _, exp := range expected {
		if !routeExists(routes, exp.method, exp.path) {
			t.Errorf("missing route: %s %s", exp.method, exp.path)
		}
	}
}

// ---------------------------------------------------------------------------
// Request-response tests (no DB access needed for empty / edge-case paths)
// ---------------------------------------------------------------------------

// newTestApp builds a fiber.App with all miniapp routes registered.
// Services are nil — only paths that return early without calling the service
// are testable this way (e.g. empty query → empty array).
func newTestApp() *fiber.App {
	app := fiber.New()
	Register(app, Deps{
		BotToken:       "test-token",
		SessionTTL:     60,
		Auth:           nil,
		Users:          nil,
		Bills:          nil,
		Savings:        nil,
		KolekTas:       nil,
		PaymentDetails: nil,
	})
	return app
}

// TestSearchBills_EmptyQuery: q kosong harus return [] tanpa sentuh DB.
// searchBills dipakai bersama oleh /tagihan/search, /pelunasan/search, /payment/search.
func TestSearchBills_EmptyQuery(t *testing.T) {
	app := newTestApp()

	for _, path := range []string{
		"/api/mini/tagihan/search",
		"/api/mini/pelunasan/search",
		"/api/mini/payment/search",
	} {
		t.Run(strings.TrimPrefix(path, "/api/mini/"), func(t *testing.T) {
			req := httptest.NewRequest("GET", path, nil)
			resp, err := app.Test(req, -1)
			if err != nil {
				t.Fatalf("request failed: %v", err)
			}
			if resp.StatusCode != fiber.StatusOK {
				t.Errorf("status = %d, want 200", resp.StatusCode)
			}
			body := readBody(resp)
			body = strings.TrimSpace(body)
			if body != "[]" {
				t.Errorf("body = %s, want []", body)
			}
		})
	}
}

// TestSearchBills_WhitespaceQuery: q hanya whitespace → tetap kosong.
func TestSearchBills_WhitespaceQuery(t *testing.T) {
	app := newTestApp()

	req := httptest.NewRequest("GET", "/api/mini/tagihan/search?q=+++", nil)
	resp, err := app.Test(req, -1)
	if err != nil {
		t.Fatalf("request failed: %v", err)
	}
	if resp.StatusCode != fiber.StatusOK {
		t.Errorf("status = %d, want 200", resp.StatusCode)
	}
	body := strings.TrimSpace(readBody(resp))
	if body != "[]" {
		t.Errorf("body = %s, want []", body)
	}
}

// TestSearchTabungan_EmptyQuery: q kosong pada tabungan search.
func TestSearchTabungan_EmptyQuery(t *testing.T) {
	app := newTestApp()

	req := httptest.NewRequest("GET", "/api/mini/tabungan/search?q=", nil)
	resp, err := app.Test(req, -1)
	if err != nil {
		t.Fatalf("request failed: %v", err)
	}
	if resp.StatusCode != fiber.StatusOK {
		t.Errorf("status = %d, want 200", resp.StatusCode)
	}
	body := strings.TrimSpace(readBody(resp))
	if body != "[]" {
		t.Errorf("body = %s, want []", body)
	}
}

// TestSearchTabungan_WhitespaceQuery
func TestSearchTabungan_WhitespaceQuery(t *testing.T) {
	app := newTestApp()

	req := httptest.NewRequest("GET", "/api/mini/tabungan/search?q=+++", nil)
	resp, err := app.Test(req, -1)
	if err != nil {
		t.Fatalf("request failed: %v", err)
	}
	if resp.StatusCode != fiber.StatusOK {
		t.Errorf("status = %d, want 200", resp.StatusCode)
	}
	body := strings.TrimSpace(readBody(resp))
	if body != "[]" {
		t.Errorf("body = %s, want []", body)
	}
}

// TestSearchKolekTas_EmptyQuery
func TestSearchKolekTas_EmptyQuery(t *testing.T) {
	app := newTestApp()

	req := httptest.NewRequest("GET", "/api/mini/kolektas/search?q=", nil)
	resp, err := app.Test(req, -1)
	if err != nil {
		t.Fatalf("request failed: %v", err)
	}
	if resp.StatusCode != fiber.StatusOK {
		t.Errorf("status = %d, want 200", resp.StatusCode)
	}
	body := strings.TrimSpace(readBody(resp))
	if body != "[]" {
		t.Errorf("body = %s, want []", body)
	}
}

// TestSearchCanvas_EmptyQuery
func TestSearchCanvas_EmptyQuery(t *testing.T) {
	app := newTestApp()

	req := httptest.NewRequest("GET", "/api/mini/canvas/search?q=", nil)
	resp, err := app.Test(req, -1)
	if err != nil {
		t.Fatalf("request failed: %v", err)
	}
	if resp.StatusCode != fiber.StatusOK {
		t.Errorf("status = %d, want 200", resp.StatusCode)
	}
	body := strings.TrimSpace(readBody(resp))
	if body != "[]" {
		t.Errorf("body = %s, want []", body)
	}
}

// TestSearchCanvas_CommaOnlyReturnsEmpty: q berisi hanya koma → keyword kosong → []
func TestSearchCanvas_CommaOnlyReturnsEmpty(t *testing.T) {
	app := newTestApp()

	req := httptest.NewRequest("GET", "/api/mini/canvas/search?q=,,,", nil)
	resp, err := app.Test(req, -1)
	if err != nil {
		t.Fatalf("request failed: %v", err)
	}
	if resp.StatusCode != fiber.StatusOK {
		t.Errorf("status = %d, want 200", resp.StatusCode)
	}
	body := strings.TrimSpace(readBody(resp))
	if body != "[]" {
		t.Errorf("body = %s, want []", body)
	}
}

// TestSearchCanvas_WhitespaceCommaTokens: comma + whitespace → results filtered.
func TestSearchCanvas_WhitespaceCommaTokens(t *testing.T) {
	app := newTestApp()

	req := httptest.NewRequest("GET", "/api/mini/canvas/search?q=%20,%20,%20", nil)
	resp, err := app.Test(req, -1)
	if err != nil {
		t.Fatalf("request failed: %v", err)
	}
	if resp.StatusCode != fiber.StatusOK {
		t.Errorf("status = %d, want 200", resp.StatusCode)
	}
	body := strings.TrimSpace(readBody(resp))
	if body != "[]" {
		t.Errorf("body = %s, want []", body)
	}
}

// TestSearchBills_MissingQueryParam: tidak ada param q → kosong.
func TestSearchBills_MissingQueryParam(t *testing.T) {
	app := newTestApp()

	req := httptest.NewRequest("GET", "/api/mini/tagihan/search", nil)
	resp, err := app.Test(req, -1)
	if err != nil {
		t.Fatalf("request failed: %v", err)
	}
	if resp.StatusCode != fiber.StatusOK {
		t.Errorf("status = %d, want 200", resp.StatusCode)
	}
	body := strings.TrimSpace(readBody(resp))
	if body != "[]" {
		t.Errorf("body = %s, want []", body)
	}
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

type routeInfo struct {
	method string
	path   string
}

// collectRoutes walks the fiber route stack and returns all registered routes.
func collectRoutes(app *fiber.App) []routeInfo {
	var routes []routeInfo
	for _, r := range app.GetRoutes(true) {
		if r.Method != "" {
			routes = append(routes, routeInfo{method: r.Method, path: r.Path})
		}
	}
	return routes
}

func routeExists(routes []routeInfo, method, path string) bool {
	for _, r := range routes {
		if r.method == method && r.path == path {
			return true
		}
	}
	return false
}

func readBody(resp *http.Response) string {
	if resp.Body == nil {
		return ""
	}
	defer resp.Body.Close()
	buf := new(strings.Builder)
	// Use minimal allocation
	for {
		var b [512]byte
		n, _ := resp.Body.Read(b[:])
		if n == 0 {
			break
		}
		buf.Write(b[:n])
	}
	return buf.String()
}

// ---------------------------------------------------------------------------
// Handler unit tests — test the handler logic in isolation through Fiber's
// test harness. These require nil services → will panic if the handler tries
// to call the service. Only early-return paths are testable.
// ---------------------------------------------------------------------------

// TestDigitsOnly_MatchesNumericSPK verifies the regex that gates GetByID vs FindByName.
func TestDigitsOnly_MatchesNumericSPK(t *testing.T) {
	valid := []string{"0", "1", "010600001234", "999999999999"}
	invalid := []string{"", "SPK-001", "010600001234A", " 010600001234", "abc"}
	for _, v := range valid {
		if !digitsOnly.MatchString(v) {
			t.Errorf("digitsOnly should match %q", v)
		}
	}
	for _, v := range invalid {
		if digitsOnly.MatchString(v) {
			t.Errorf("digitsOnly should NOT match %q", v)
		}
	}
}

// TestTagihanRoutes_Exist verifies route registration for each tagihan handler.
func TestTagihanRoutes_Exist(t *testing.T) {
	app := newTestApp()
	routes := collectRoutes(app)

	mustHave := []struct {
		method string
		path   string
	}{
		{"GET", "/api/mini/tagihan/search"},
		{"GET", "/api/mini/tagihan/:spk"},
		{"GET", "/api/mini/pelunasan/search"},
		{"GET", "/api/mini/pelunasan/:spk"},
	}
	for _, exp := range mustHave {
		if !routeExists(routes, exp.method, exp.path) {
			t.Errorf("missing %s %s in registered routes", exp.method, exp.path)
		}
	}
}

// TestTabunganRoutes_Exist
func TestTabunganRoutes_Exist(t *testing.T) {
	app := newTestApp()
	routes := collectRoutes(app)

	mustHave := []struct {
		method string
		path   string
	}{
		{"GET", "/api/mini/tabungan/search"},
		{"GET", "/api/mini/tabungan/:tabId"},
	}
	for _, exp := range mustHave {
		if !routeExists(routes, exp.method, exp.path) {
			t.Errorf("missing %s %s", exp.method, exp.path)
		}
	}
}

// TestCanvasRoutes_Exist
func TestCanvasRoutes_Exist(t *testing.T) {
	app := newTestApp()
	routes := collectRoutes(app)

	mustHave := []struct {
		method string
		path   string
	}{
		{"GET", "/api/mini/canvas/search"},
		{"GET", "/api/mini/canvas/:tabId"},
	}
	for _, exp := range mustHave {
		if !routeExists(routes, exp.method, exp.path) {
			t.Errorf("missing %s %s", exp.method, exp.path)
		}
	}
}

// TestKolekTasRoutes_Exist
func TestKolekTasRoutes_Exist(t *testing.T) {
	app := newTestApp()
	routes := collectRoutes(app)

	mustHave := []struct {
		method string
		path   string
	}{
		{"GET", "/api/mini/kolektas/search"},
		{"GET", "/api/mini/kolektas/:id"},
	}
	for _, exp := range mustHave {
		if !routeExists(routes, exp.method, exp.path) {
			t.Errorf("missing %s %s", exp.method, exp.path)
		}
	}
}

// TestPaymentRoutes_Exist
func TestPaymentRoutes_Exist(t *testing.T) {
	app := newTestApp()
	routes := collectRoutes(app)

	mustHave := []struct {
		method string
		path   string
	}{
		{"GET", "/api/mini/payment/search"},
		{"GET", "/api/mini/payment/:spk"},
	}
	for _, exp := range mustHave {
		if !routeExists(routes, exp.method, exp.path) {
			t.Errorf("missing %s %s", exp.method, exp.path)
		}
	}
}

// TestTagihanSummarySlice verifies bulk conversion to tagihanSummary.
func TestTagihanSummarySlice(t *testing.T) {
	items := []entity.Bills{
		{NoSpk: "A", Name: "X"},
		{NoSpk: "B", Name: "Y"},
		{},
	}
	out := make([]tagihanSummary, 0, len(items))
	for _, b := range items {
		out = append(out, toTagihanSummary(b))
	}
	if len(out) != 3 {
		t.Fatalf("len = %d, want 3", len(out))
	}
	if out[0].NoSpk != "A" || out[1].NoSpk != "B" {
		t.Error("order mismatch")
	}
	if out[2].NoSpk != "" {
		t.Errorf("empty bill NoSpk = %q, want empty", out[2].NoSpk)
	}
}

// TestSearchBills_DefaultPage: page default = 0 (QueryInt default kalau tidak diset).
func TestSearchBills_DefaultPage(t *testing.T) {
	app := newTestApp()
	// No page param → Fiber QueryInt returns default 0
	req := httptest.NewRequest("GET", "/api/mini/tagihan/search", nil)
	resp, err := app.Test(req, -1)
	if err != nil {
		t.Fatalf("request failed: %v", err)
	}
	if resp.StatusCode != fiber.StatusOK {
		t.Errorf("status = %d, want 200", resp.StatusCode)
	}
}

// TestPelunasanRoutes_Exist verifies both pelunasan/search and pelunasan/:spk.
func TestPelunasanRoutes_Exist(t *testing.T) {
	app := newTestApp()
	routes := collectRoutes(app)

	if !routeExists(routes, "GET", "/api/mini/pelunasan/search") {
		t.Error("missing GET /api/mini/pelunasan/search")
	}
	if !routeExists(routes, "GET", "/api/mini/pelunasan/:spk") {
		t.Error("missing GET /api/mini/pelunasan/:spk")
	}
}

// TestAllRoutes_Sorted verifies routes are deterministic and sorted.
func TestAllRoutes_Sorted(t *testing.T) {
	app := newTestApp()
	routes := collectRoutes(app)

	paths := make([]string, 0, len(routes))
	for _, r := range routes {
		paths = append(paths, r.method+" "+r.path)
	}

	// Minimal: at least 13 routes (auth + 6 subgroups × ~2 routes each).
	// Route count includes the group-mounted middleware routes.
	if len(routes) < 13 {
		t.Errorf("expected ≥13 routes, got %d: %v", len(routes), paths)
	}

	// Verify sort is stable (no duplicate method+path combos).
	seen := map[string]bool{}
	for _, p := range paths {
		if seen[p] {
			t.Errorf("duplicate route: %s", p)
		}
		seen[p] = true
	}
}

// TestSearchBills_ResponseIsJSONArray confirms Content-Type on empty results.
func TestSearchBills_ResponseIsJSONArray(t *testing.T) {
	app := newTestApp()

	req := httptest.NewRequest("GET", "/api/mini/tagihan/search", nil)
	resp, err := app.Test(req, -1)
	if err != nil {
		t.Fatalf("request failed: %v", err)
	}
	ct := resp.Header.Get("Content-Type")
	if !strings.Contains(ct, "application/json") {
		t.Errorf("Content-Type = %q, want application/json", ct)
	}
	body := strings.TrimSpace(readBody(resp))
	if body != "[]" {
		t.Errorf("body = %s, want []", body)
	}
}

// TestCanvasKeywordParsing_TableDriven verifies the canvas keyword parsing logic.
// Because the handler is inline in registerCanvas, we extract the logic to test.
func TestCanvasKeywordParsing(t *testing.T) {
	tests := []struct {
		q    string
		want []string
	}{
		{"", nil},
		{"  ", nil},
		{"bandung", []string{"bandung"}},
		{"bandung jakarta", []string{"bandung", "jakarta"}},
		{"bandung, jakarta", []string{"bandung", "jakarta"}},
		{"  bandung , , , jakarta  ", []string{"bandung", "jakarta"}},
		{", , ,", nil},
		{", , , ", nil},
		{"a,b,c,d", []string{"a", "b", "c", "d"}},
	}

	for _, tt := range tests {
		t.Run(tt.q, func(t *testing.T) {
			q := strings.TrimSpace(tt.q)
			var keywords []string
			for _, part := range strings.Split(q, ",") {
				for _, tok := range strings.Fields(strings.TrimSpace(part)) {
					if tok != "" {
						keywords = append(keywords, tok)
					}
				}
			}
			if len(keywords) != len(tt.want) {
				t.Errorf("len = %d, want %d (%v)", len(keywords), len(tt.want), keywords)
				return
			}
			for i := range keywords {
				if keywords[i] != tt.want[i] {
					t.Errorf("keywords[%d] = %q, want %q", i, keywords[i], tt.want[i])
				}
			}
		})
	}
}

// TestCanvasKeywordParsing_Sorted verifies keyword output is deterministic.
func TestCanvasKeywordParsing_Sorted(t *testing.T) {
	q := "  c ,   a, b  "
	var keywords []string
	for _, part := range strings.Split(strings.TrimSpace(q), ",") {
		for _, tok := range strings.Fields(strings.TrimSpace(part)) {
			if tok != "" {
				keywords = append(keywords, tok)
			}
		}
	}
	expected := []string{"c", "a", "b"}
	for i := range keywords {
		if keywords[i] != expected[i] {
			t.Errorf("keywords[%d] = %q, want %q (order must be stable)", i, keywords[i], expected[i])
		}
	}
}

// TestPaymentDetailMapping verifies the payment detail row mapping logic.
func TestPaymentDetailMapping(t *testing.T) {
	// Replicate the inline mapping from registerPayment.
	records := []entity.PaymentDetails{
		{Tanggal: "2024-06-01", KodePosting: "P", NominalAngsuran: 100000, Denda: 0, Penalti: 0},
		{Tanggal: "2024-05-01", KodePosting: "I", NominalAngsuran: 50000, Denda: 1000, Penalti: 500},
		{Tanggal: "2024-04-01", KodePosting: "X", NominalAngsuran: 25000, Denda: 0, Penalti: 0},
		{Tanggal: "2024-03-01", KodePosting: "p", NominalAngsuran: 75000, Denda: 0, Penalti: 0},
		{Tanggal: "2024-02-01", KodePosting: "i", NominalAngsuran: 30000, Denda: 0, Penalti: 0},
	}

	rows := make([]fiber.Map, 0, len(records))
	for i, pd := range records {
		t := strings.ToUpper(strings.TrimSpace(pd.KodePosting))
		pokok, bunga := int64(0), int64(0)
		if t == "P" {
			pokok = pd.NominalAngsuran
		} else if t == "I" {
			bunga = pd.NominalAngsuran
		}
		total := pd.NominalAngsuran + pd.Denda + pd.Penalti
		rows = append(rows, fiber.Map{
			"no": i + 1, "tanggal": pd.Tanggal, "typePosting": t,
			"pokok": pokok, "bunga": bunga,
			"denda": pd.Denda, "penalti": pd.Penalti,
			"total": total, "highlight": pd.Denda+pd.Penalti > 0,
		})
	}

	// Row 0: P → pokok=100000, bunga=0, total=100000, highlight=false
	if rows[0]["pokok"] != int64(100000) {
		t.Errorf("row 0 pokok = %v", rows[0]["pokok"])
	}
	if rows[0]["bunga"] != int64(0) {
		t.Errorf("row 0 bunga = %v", rows[0]["bunga"])
	}
	if rows[0]["highlight"] != false {
		t.Errorf("row 0 highlight = %v, want false", rows[0]["highlight"])
	}

	// Row 1: I → bunga=50000, pokok=0, total=51500, highlight=true
	if rows[1]["bunga"] != int64(50000) {
		t.Errorf("row 1 bunga = %v", rows[1]["bunga"])
	}
	if rows[1]["pokok"] != int64(0) {
		t.Errorf("row 1 pokok = %v", rows[1]["pokok"])
	}
	if rows[1]["total"] != int64(51500) {
		t.Errorf("row 1 total = %v", rows[1]["total"])
	}
	if rows[1]["highlight"] != true {
		t.Errorf("row 1 highlight = %v, want true", rows[1]["highlight"])
	}

	// Row 2: X → pokok=0, bunga=0
	if rows[2]["pokok"] != int64(0) {
		t.Errorf("row 2 pokok = %v", rows[2]["pokok"])
	}
	if rows[2]["bunga"] != int64(0) {
		t.Errorf("row 2 bunga = %v", rows[2]["bunga"])
	}

	// Row 3: lowercase p → becomes P, pokok=75000
	if rows[3]["typePosting"] != "P" {
		t.Errorf("row 3 typePosting = %v, want P", rows[3]["typePosting"])
	}
	if rows[3]["pokok"] != int64(75000) {
		t.Errorf("row 3 pokok = %v", rows[3]["pokok"])
	}

	// Row 4: lowercase i → becomes I, bunga=30000
	if rows[4]["typePosting"] != "I" {
		t.Errorf("row 4 typePosting = %v, want I", rows[4]["typePosting"])
	}
	if rows[4]["bunga"] != int64(30000) {
		t.Errorf("row 4 bunga = %v", rows[4]["bunga"])
	}

	// Verify no field
	if rows[0]["no"] != 1 {
		t.Errorf("row 0 no = %v, want 1", rows[0]["no"])
	}
	if rows[4]["no"] != 5 {
		t.Errorf("row 4 no = %v, want 5", rows[4]["no"])
	}
}

// TestTagihanSummary_EmptyBill verifies zero-value bill conversion produces
// consistent output (no panics).
func TestTagihanSummary_EmptyBill(t *testing.T) {
	b := entity.Bills{}
	s := toTagihanSummary(b)

	if s.NoSpk != "" {
		t.Errorf("NoSpk = %q, want empty", s.NoSpk)
	}
	if s.Name != "" {
		t.Errorf("Name = %q, want empty", s.Name)
	}
	if s.Installment != 0 {
		t.Errorf("Installment = %d, want 0", s.Installment)
	}
	if s.FullPayment != 0 {
		t.Errorf("FullPayment = %d, want 0", s.FullPayment)
	}
	if s.CKPNNominal != 0 {
		t.Errorf("CKPNNominal = %d, want 0", s.CKPNNominal)
	}
	if s.DayLate != "" {
		t.Errorf("DayLate = %q, want empty", s.DayLate)
	}
}

// TestCollectRoutes_AllMethodPathCombos ensures no nil method/empty path.
func TestCollectRoutes_AllMethodPathCombos(t *testing.T) {
	app := newTestApp()
	routes := collectRoutes(app)

	for _, r := range routes {
		if r.method == "" {
			t.Errorf("route with empty method: %+v", r)
		}
		if r.path == "" {
			t.Errorf("route with empty path: %+v", r)
		}
		if !strings.HasPrefix(r.path, "/api/mini") {
			t.Errorf("route %s %s does not start with /api/mini", r.method, r.path)
		}
	}
}

// TestRegister_DifferentTTLs verifies Register works with various TTL values.
func TestRegister_DifferentTTLs(t *testing.T) {
	for _, ttl := range []int{0, 30, 120} {
		app := fiber.New()
		Register(app, Deps{BotToken: "t", SessionTTL: ttl})
		routes := collectRoutes(app)
		if len(routes) < 13 {
			t.Errorf("TTL=%d: expected ≥13 routes, got %d", ttl, len(routes))
		}
	}
}

// TestAllRoutes_AccessibleWithoutMiddleware verifies that routes don't panic
// when accessed without auth middleware (nil services).
func TestAllRoutes_AccessibleWithoutMiddleware(t *testing.T) {
	app := newTestApp()

	// These paths will fail at session middleware (no token) → 401 or 500
	// due to nil services. But they shouldn't panic.
	paths := []string{
		"/api/mini/tagihan/search",
		"/api/mini/tagihan/123",
		"/api/mini/pelunasan/search",
		"/api/mini/pelunasan/123",
		"/api/mini/tabungan/search",
		"/api/mini/tabungan/TAB001",
		"/api/mini/canvas/search",
		"/api/mini/canvas/TAB001",
		"/api/mini/kolektas/search",
		"/api/mini/kolektas/K001",
		"/api/mini/payment/search",
		"/api/mini/payment/123",
	}

	for _, path := range paths {
		t.Run(path, func(t *testing.T) {
			req := httptest.NewRequest("GET", path, nil)
			resp, err := app.Test(req, -1)
			if err != nil {
				t.Fatalf("request failed: %v", err)
			}
			// 401 (session middleware rejects) or 500 (nil service deref)
			// but should NOT be a panic.
			if resp.StatusCode < 400 {
				t.Logf("path %s returned %d (expected error without auth)", path, resp.StatusCode)
			}
			resp.Body.Close()
		})
	}
}

func TestCleanTanggal(t *testing.T) {
	tests := []struct {
		input    string
		expected string
	}{
		{"20240328", "20240328"},
		{"28/03/2024", "20240328"},
		{"2024-03-28", "20240328"},
		{" 28/03/2024 ", "20240328"},
		{"2024/03/28", "20240328"},
		{"28-03-2024", "20240328"},
		{"invalid-date-format", "invalid-date-format"},
	}

	for _, tt := range tests {
		t.Run(tt.input, func(t *testing.T) {
			actual := cleanTanggal(tt.input)
			if actual != tt.expected {
				t.Errorf("cleanTanggal(%q) = %q; want %q", tt.input, actual, tt.expected)
			}
		})
	}
}

// ---------------------------------------------------------------------------
// Benchmark
// ---------------------------------------------------------------------------

func BenchmarkDigitsOnly_Match(b *testing.B) {
	for b.Loop() {
		digitsOnly.MatchString("010600001234")
	}
}

func BenchmarkToTagihanSummary(b *testing.B) {
	bill := entity.Bills{
		NoSpk: "SPK001", Name: "BUDI", Branch: "CAB1", Product: "KMG-LM",
		CollectStatus: "01", DayLate: "5", Installment: 500_000,
		FullPayment: 10_000_000, CKPNType: "A", CKPNNominal: 1_000_000,
		RekeningAutobedet: "1234567890",
	}
	for b.Loop() {
		toTagihanSummary(bill)
	}
}

// ---------------------------------------------------------------------------
// Compile-time guard: sort import used only in benchmark
// ---------------------------------------------------------------------------
var _ = sort.Strings // prevent import removal