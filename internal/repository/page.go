package repository

// Page adalah parameter paginasi standar yang dipakai semua repository.
// Padanan langsung dari Spring Data Pageable: page 0-based, size > 0.
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
		return 20
	}
	return p.Size
}
