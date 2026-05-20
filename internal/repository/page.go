package repository

// Page adalah parameter paginasi standar yang dipakai semua repository.
// Padanan langsung dari Spring Data Pageable: page 0-based, size > 0.
//
// Konvensi khusus: Size <= 0 → unlimited (skip pagination sepenuhnya).
// Caller pakai Page{} atau Page{Size: 0} untuk "ambil semua hasil filter".
type Page struct {
	Page int64
	Size int64
}

func (p Page) Skip() int64 {
	if p.Page <= 0 {
		return 0
	}
	return p.Page * p.Size
}

func (p Page) Limit() int64 {
	if p.Size <= 0 {
		return 0
	}
	return p.Size
}

// Unlimited true kalau caller mau ambil semua hasil tanpa pagination.
// Repo pakai ini untuk skip SetSkip/SetLimit sepenuhnya.
func (p Page) Unlimited() bool { return p.Size <= 0 }
