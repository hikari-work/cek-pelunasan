package miniapp

import (
	"regexp"
	"strings"

	"github.com/gofiber/fiber/v2"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
	"github.com/hikari-work/cek-pelunasan/internal/service/bill"
	"github.com/hikari-work/cek-pelunasan/internal/service/kolektas"
	"github.com/hikari-work/cek-pelunasan/internal/service/paymentdetails"
	"github.com/hikari-work/cek-pelunasan/internal/service/pelunasan"
	"github.com/hikari-work/cek-pelunasan/internal/service/savings"
)

var digitsOnly = regexp.MustCompile(`^\d+$`)

// tagihanSummary = padanan TagihanSummaryDTO. Field apa adanya dari Bills.
type tagihanSummary struct {
	NoSpk             string `json:"noSpk"`
	Name              string `json:"name"`
	Branch            string `json:"branch"`
	Product           string `json:"product"`
	CollectStatus     string `json:"collectStatus"`
	DayLate           string `json:"dayLate"`
	Installment       int64  `json:"installment"`
	FullPayment       int64  `json:"fullPayment"`
	CKPNType          string `json:"ckpnType"`
	CKPNNominal       int64  `json:"ckpnNominal"`
	RekeningAutobedet string `json:"rekeningAutobedet"`
}

func toTagihanSummary(b entity.Bills) tagihanSummary {
	return tagihanSummary{
		NoSpk: b.NoSpk, Name: b.Name, Branch: b.Branch, Product: b.Product,
		CollectStatus: b.CollectStatus, DayLate: b.DayLate,
		Installment: b.Installment, FullPayment: b.FullPayment,
		CKPNType: b.CKPNType, CKPNNominal: b.CKPNNominal,
		RekeningAutobedet: b.RekeningAutobedet,
	}
}

func registerTagihan(r fiber.Router, svc *bill.Service) {
	g := r.Group("/tagihan")
	g.Get("/search", searchBills(svc))
	g.Get("/:spk", func(c *fiber.Ctx) error {
		b, err := svc.GetByID(c.UserContext(), c.Params("spk"))
		if err != nil {
			return c.SendStatus(fiber.StatusInternalServerError)
		}
		if b == nil {
			return c.SendStatus(fiber.StatusNotFound)
		}
		return c.JSON(b)
	})
}

func registerPelunasan(r fiber.Router, svc *bill.Service) {
	g := r.Group("/pelunasan")
	g.Get("/search", searchBills(svc))
	g.Get("/:spk", func(c *fiber.Ctx) error {
		b, err := svc.GetByID(c.UserContext(), c.Params("spk"))
		if err != nil {
			return c.SendStatus(fiber.StatusInternalServerError)
		}
		if b == nil {
			return c.SendStatus(fiber.StatusNotFound)
		}
		// Hitung pelunasan via PelunasanService. Kalau gagal (mis. format
		// produk/tanggal nyentrik), fallback ke Bills basic supaya client
		// tetap dapat data — sama persis kontrak field dengan miniapp legacy.
		res, calcErr := pelunasan.Calculate(b)
		if calcErr != nil {
			return c.JSON(fiber.Map{
				"spk": b.NoSpk, "nama": b.Name, "alamat": b.Address, "product": b.Product,
				"tglRealisasi": b.Realization, "tglJatuhTempo": b.DueDate, "rencanaPelunasan": nil,
				"plafond": b.Plafond, "bakiDebet": b.DebitTray,
				"perhitunganBunga": nil, "typeBunga": nil,
				"penalty": nil, "multiplierPenalty": nil, "denda": nil,
				"totalPelunasan": b.FullPayment,
			})
		}
		return c.JSON(fiber.Map{
			"spk":               res.SPK,
			"nama":              res.Nama,
			"alamat":            res.Alamat,
			"product":           b.Product,
			"tglRealisasi":      res.TglRealisasi,
			"tglJatuhTempo":     res.TglJatuhTempo,
			"rencanaPelunasan":  res.RencanaPelunasan,
			"plafond":           res.Plafond,
			"bakiDebet":         res.BakiDebet,
			"perhitunganBunga":  res.PerhitunganBunga,
			"typeBunga":         res.TypeBunga,
			"penalty":           res.Penalty,
			"multiplierPenalty": res.MultiplierPenalty,
			"denda":             res.Denda,
			"totalPelunasan":    res.TotalPelunasan(),
		})
	})
}

// searchBills: q numeric -> getBillById; selain itu -> findByName.
func searchBills(svc *bill.Service) fiber.Handler {
	return func(c *fiber.Ctx) error {
		q := strings.TrimSpace(c.Query("q"))
		if q == "" {
			return c.JSON([]any{})
		}
		page := int64(c.QueryInt("page", 0))

		ctx := c.UserContext()
		if digitsOnly.MatchString(q) {
			b, err := svc.GetByID(ctx, q)
			if err != nil {
				return c.SendStatus(fiber.StatusInternalServerError)
			}
			if b == nil {
				return c.JSON([]any{})
			}
			return c.JSON([]tagihanSummary{toTagihanSummary(*b)})
		}

		res, err := svc.FindByName(ctx, q, page, 20)
		if err != nil {
			return c.SendStatus(fiber.StatusInternalServerError)
		}
		out := make([]tagihanSummary, 0, len(res.Items))
		for _, b := range res.Items {
			out = append(out, toTagihanSummary(b))
		}
		return c.JSON(out)
	}
}

func registerTabungan(r fiber.Router, svc *savings.Service) {
	g := r.Group("/tabungan")
	g.Get("/search", func(c *fiber.Ctx) error {
		q := strings.TrimSpace(c.Query("q"))
		if q == "" {
			return c.JSON([]any{})
		}
		ctx := c.UserContext()
		if digitsOnly.MatchString(q) {
			s, err := svc.FindByID(ctx, q)
			if err != nil {
				return c.SendStatus(fiber.StatusInternalServerError)
			}
			if s == nil {
				return c.JSON([]any{})
			}
			return c.JSON([]fiber.Map{tabunganSummary(*s)})
		}
		rows, err := svc.FindByName(ctx, q, 20)
		if err != nil {
			return c.SendStatus(fiber.StatusInternalServerError)
		}
		out := make([]fiber.Map, 0, len(rows))
		for _, s := range rows {
			out = append(out, tabunganSummary(s))
		}
		return c.JSON(out)
	})
	g.Get("/:tabId", func(c *fiber.Ctx) error {
		s, err := svc.FindByID(c.UserContext(), c.Params("tabId"))
		if err != nil {
			return c.SendStatus(fiber.StatusInternalServerError)
		}
		if s == nil {
			return c.SendStatus(fiber.StatusNotFound)
		}
		return c.JSON(s)
	})
}

func tabunganSummary(s entity.Savings) fiber.Map {
	return fiber.Map{
		"tabId": s.TabID, "name": s.Name, "branch": s.Branch,
		"type": s.Type, "balance": s.Balance,
	}
}

func registerCanvas(r fiber.Router, svc *savings.Service) {
	g := r.Group("/canvas")
	g.Get("/search", func(c *fiber.Ctx) error {
		q := strings.TrimSpace(c.Query("q"))
		if q == "" {
			return c.JSON([]any{})
		}
		var keywords []string
		for _, part := range strings.Split(q, ",") {
			for _, tok := range strings.Fields(strings.TrimSpace(part)) {
				if tok != "" {
					keywords = append(keywords, tok)
				}
			}
		}
		if len(keywords) == 0 {
			return c.JSON([]any{})
		}
		res, err := svc.FindFiltered(c.UserContext(), keywords, 0, 500)
		if err != nil {
			return c.SendStatus(fiber.StatusInternalServerError)
		}
		out := make([]fiber.Map, 0, len(res.Items))
		for _, s := range res.Items {
			out = append(out, fiber.Map{
				"tabId": s.TabID, "name": s.Name, "branch": s.Branch,
				"type": s.Type, "balance": s.Balance,
				"cif": s.CIF, "address": s.Address,
			})
		}
		return c.JSON(out)
	})
	g.Get("/:tabId", func(c *fiber.Ctx) error {
		s, err := svc.FindByID(c.UserContext(), c.Params("tabId"))
		if err != nil {
			return c.SendStatus(fiber.StatusInternalServerError)
		}
		if s == nil {
			return c.SendStatus(fiber.StatusNotFound)
		}
		return c.JSON(s)
	})
}

func registerKolekTas(r fiber.Router, svc *kolektas.Service) {
	g := r.Group("/kolektas")
	g.Get("/search", func(c *fiber.Ctx) error {
		q := strings.TrimSpace(c.Query("q"))
		if q == "" {
			return c.JSON([]any{})
		}
		// Tampilkan semua anggota kelompok — pakai page large.
		res, err := svc.FindByKelompok(c.UserContext(), q, 1, 1_000_000)
		if err != nil {
			return c.SendStatus(fiber.StatusInternalServerError)
		}
		return c.JSON(res.Items)
	})
	g.Get("/:id", func(c *fiber.Ctx) error {
		id := c.Params("id")
		item, err := svc.FindByID(c.UserContext(), id)
		if err != nil {
			return c.SendStatus(fiber.StatusInternalServerError)
		}
		if item == nil {
			return c.SendStatus(fiber.StatusNotFound)
		}
		return c.JSON(item)
	})
}

func registerPayment(r fiber.Router, billSvc *bill.Service, pdSvc *paymentdetails.Service) {
	g := r.Group("/payment")
	g.Get("/search", searchBills(billSvc))
	g.Get("/:spk", func(c *fiber.Ctx) error {
		spk := c.Params("spk")
		ctx := c.UserContext()
		b, err := billSvc.GetByID(ctx, spk)
		if err != nil {
			return c.SendStatus(fiber.StatusInternalServerError)
		}
		if b == nil {
			return c.SendStatus(fiber.StatusNotFound)
		}
		records, err := pdSvc.FindByNoSpk(ctx, spk)
		if err != nil {
			return c.SendStatus(fiber.StatusInternalServerError)
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
		return c.JSON(fiber.Map{
			"noSpk": b.NoSpk, "name": b.Name, "branch": b.Branch, "product": b.Product,
			"rows":           rows,
			"tunggakanPokok": b.LastPrincipal,
			"tunggakanBunga": b.LastInterest,
			"minimalPokok":   b.MinPrincipal,
			"minimalBunga":   b.MinInterest,
		})
	})
}
