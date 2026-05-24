// Package ideb provides HTTP handler for iDeb HTML generation endpoint.
// This is a Go replacement for the PHP generate.php endpoint.
package ideb

import (
	"io"
	"log/slog"
	"strings"

	"github.com/gofiber/fiber/v2"
	"github.com/hikari-work/cek-pelunasan/internal/service/slik"
)

// Handler handles iDeb HTML generation requests.
type Handler struct {
	Generator *slik.HTMLGenerator
}

// Generate handles POST /ideb/generate endpoint.
// Accepts multipart file upload with:
//   - fileToUpload: .txt file containing SLIK JSON data
//   - fasilitasAktif: "y" or "n" (optional, default "n")
//
// Returns HTML report or error message.
func (h *Handler) Generate(c *fiber.Ctx) error {
	// Parse multipart form
	file, err := c.FormFile("fileToUpload")
	if err != nil {
		slog.Warn("ideb: no file uploaded", "err", err)
		return c.Status(fiber.StatusBadRequest).SendString(
			"Mohon maaf hanya file (.txt) hasil export dari aplikasi iDeb Viewer yang diperbolehkan.")
	}

	// Validate file extension
	if !strings.HasSuffix(strings.ToLower(file.Filename), ".txt") {
		slog.Warn("ideb: invalid file extension", "filename", file.Filename)
		return c.Status(fiber.StatusBadRequest).SendString(
			"Mohon maaf hanya file (.txt) hasil export dari aplikasi iDeb Viewer yang diperbolehkan.")
	}

	// Open and read file
	fileHandle, err := file.Open()
	if err != nil {
		slog.Error("ideb: failed to open uploaded file", "err", err)
		return c.Status(fiber.StatusInternalServerError).SendString("Gagal membaca file.")
	}
	defer fileHandle.Close()

	fileData, err := io.ReadAll(fileHandle)
	if err != nil {
		slog.Error("ideb: failed to read file data", "err", err)
		return c.Status(fiber.StatusInternalServerError).SendString("Gagal membaca file.")
	}

	// Parse SLIK JSON
	dto, err := slik.ParseSlikJSON(fileData)
	if err != nil {
		slog.Error("ideb: failed to parse SLIK JSON", "err", err, "filename", file.Filename)
		return c.Status(fiber.StatusBadRequest).SendString(
			"Format file tidak valid. Pastikan file adalah hasil export dari iDeb Viewer.")
	}

	// Get fasilitasAktif parameter
	fasilitasAktif := strings.ToLower(strings.TrimSpace(c.FormValue("fasilitasAktif"))) == "y"

	// Override footer fields if provided in form (legacy PHP compatibility)
	tujuan := c.FormValue("tujuan")
	if tujuan != "" {
		dto.Header.TujuanPenggunaan = tujuan
	}
	petugas := c.FormValue("petugas")
	if petugas != "" {
		dto.Header.PetugasPermintaan = petugas
	}

	// Generate HTML
	html := h.Generator.GenerateHTML(dto, fasilitasAktif)
	if html == "" {
		slog.Error("ideb: HTML generation returned empty result")
		return c.Status(fiber.StatusInternalServerError).SendString("Gagal generate HTML.")
	}

	slog.Info("ideb: HTML generated successfully",
		"filename", file.Filename,
		"size", len(html),
		"fasilitasAktif", fasilitasAktif)

	// Return HTML
	c.Set("Content-Type", "text/html; charset=utf-8")
	return c.SendString(html)
}
