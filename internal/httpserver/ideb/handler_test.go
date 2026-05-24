package ideb

import (
	"bytes"
	"io"
	"mime/multipart"
	"net/http/httptest"
	"testing"

	"github.com/gofiber/fiber/v2"
	"github.com/hikari-work/cek-pelunasan/internal/service/slik"
)

func TestHandler_Generate(t *testing.T) {
	// Sample SLIK JSON data
	jsonData := []byte(`{
		"header": {
			"kodeReferensiPengguna": "TEST-001",
			"tanggalPermintaan": "20260524120000"
		},
		"individual": {
			"dataPokokDebitur": [{
				"namaDebitur": "TEST USER",
				"noIdentitas": "1234567890123456",
				"alamat": "TEST ADDRESS"
			}],
			"fasilitas": {
				"kreditPembiayan": [{
					"ljkKet": "TEST BANK",
					"plafonAwal": "10000000",
					"bakiDebet": "5000000",
					"kondisiKet": "LANCAR",
					"kualitasKet": "1"
				}]
			}
		}
	}`)

	// Create handler
	handler := &Handler{
		Generator: &slik.HTMLGenerator{LogoURL: "logo.png"},
	}

	// Create Fiber app
	app := fiber.New()
	app.Post("/ideb/generate", handler.Generate)

	// Create multipart form
	body := &bytes.Buffer{}
	writer := multipart.NewWriter(body)

	// Add file
	part, err := writer.CreateFormFile("fileToUpload", "test.txt")
	if err != nil {
		t.Fatalf("CreateFormFile failed: %v", err)
	}
	if _, err := part.Write(jsonData); err != nil {
		t.Fatalf("Write file data failed: %v", err)
	}

	// Add fasilitasAktif field
	if err := writer.WriteField("fasilitasAktif", "n"); err != nil {
		t.Fatalf("WriteField failed: %v", err)
	}

	if err := writer.Close(); err != nil {
		t.Fatalf("Close writer failed: %v", err)
	}

	// Create request
	req := httptest.NewRequest("POST", "/ideb/generate", body)
	req.Header.Set("Content-Type", writer.FormDataContentType())

	// Execute request
	resp, err := app.Test(req, -1)
	if err != nil {
		t.Fatalf("Request failed: %v", err)
	}
	defer resp.Body.Close()

	// Check response
	if resp.StatusCode != fiber.StatusOK {
		bodyBytes, _ := io.ReadAll(resp.Body)
		t.Fatalf("Expected status 200, got %d. Body: %s", resp.StatusCode, string(bodyBytes))
	}

	// Read response body
	htmlBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		t.Fatalf("Read response failed: %v", err)
	}

	html := string(htmlBytes)

	// Validate HTML content
	if len(html) == 0 {
		t.Error("HTML response is empty")
	}
	if !contains(html, "<!DOCTYPE html>") {
		t.Error("Missing DOCTYPE")
	}
	if !contains(html, "TEST USER") {
		t.Error("Missing debitur name")
	}
	if !contains(html, "TEST BANK") {
		t.Error("Missing bank name")
	}
	if !contains(html, "Resume Informasi Debitur (iDeb)") {
		t.Error("Missing header title")
	}

	t.Logf("HTML response: %d bytes", len(html))
}

func TestHandler_Generate_InvalidExtension(t *testing.T) {
	handler := &Handler{
		Generator: &slik.HTMLGenerator{LogoURL: "logo.png"},
	}

	app := fiber.New()
	app.Post("/ideb/generate", handler.Generate)

	// Create multipart form with .json extension
	body := &bytes.Buffer{}
	writer := multipart.NewWriter(body)

	part, err := writer.CreateFormFile("fileToUpload", "test.json")
	if err != nil {
		t.Fatalf("CreateFormFile failed: %v", err)
	}
	if _, err := part.Write([]byte("{}")); err != nil {
		t.Fatalf("Write file data failed: %v", err)
	}

	if err := writer.Close(); err != nil {
		t.Fatalf("Close writer failed: %v", err)
	}

	req := httptest.NewRequest("POST", "/ideb/generate", body)
	req.Header.Set("Content-Type", writer.FormDataContentType())

	resp, err := app.Test(req, -1)
	if err != nil {
		t.Fatalf("Request failed: %v", err)
	}
	defer resp.Body.Close()

	// Should return 400 Bad Request
	if resp.StatusCode != fiber.StatusBadRequest {
		t.Errorf("Expected status 400, got %d", resp.StatusCode)
	}

	bodyBytes, _ := io.ReadAll(resp.Body)
	body_str := string(bodyBytes)
	if !contains(body_str, "hanya file (.txt)") {
		t.Errorf("Expected error message about .txt files, got: %s", body_str)
	}
}

func TestHandler_Generate_FasilitasAktif(t *testing.T) {
	jsonData := []byte(`{
		"header": {
			"kodeReferensiPengguna": "TEST-002",
			"tanggalPermintaan": "20260524120000"
		},
		"individual": {
			"dataPokokDebitur": [{
				"namaDebitur": "TEST USER",
				"noIdentitas": "1234567890123456",
				"alamat": "TEST ADDRESS"
			}],
			"fasilitas": {
				"kreditPembiayan": [
					{
						"ljkKet": "BANK AKTIF",
						"plafonAwal": "10000000",
						"bakiDebet": "5000000",
						"kondisiKet": "LANCAR",
						"kualitasKet": "1"
					},
					{
						"ljkKet": "BANK LUNAS",
						"plafonAwal": "20000000",
						"bakiDebet": "0",
						"kondisiKet": "LUNAS",
						"kualitasKet": "1"
					}
				]
			}
		}
	}`)

	handler := &Handler{
		Generator: &slik.HTMLGenerator{LogoURL: "logo.png"},
	}

	app := fiber.New()
	app.Post("/ideb/generate", handler.Generate)

	// Test with fasilitasAktif=y
	body := &bytes.Buffer{}
	writer := multipart.NewWriter(body)

	part, err := writer.CreateFormFile("fileToUpload", "test.txt")
	if err != nil {
		t.Fatalf("CreateFormFile failed: %v", err)
	}
	if _, err := part.Write(jsonData); err != nil {
		t.Fatalf("Write file data failed: %v", err)
	}

	if err := writer.WriteField("fasilitasAktif", "y"); err != nil {
		t.Fatalf("WriteField failed: %v", err)
	}

	if err := writer.Close(); err != nil {
		t.Fatalf("Close writer failed: %v", err)
	}

	req := httptest.NewRequest("POST", "/ideb/generate", body)
	req.Header.Set("Content-Type", writer.FormDataContentType())

	resp, err := app.Test(req, -1)
	if err != nil {
		t.Fatalf("Request failed: %v", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != fiber.StatusOK {
		t.Fatalf("Expected status 200, got %d", resp.StatusCode)
	}

	htmlBytes, _ := io.ReadAll(resp.Body)
	html := string(htmlBytes)

	// Should include active bank
	if !contains(html, "BANK AKTIF") {
		t.Error("Missing active bank in filtered output")
	}

	// Should NOT include closed bank
	if contains(html, "BANK LUNAS") {
		t.Error("Should not include closed bank when fasilitasAktif=y")
	}

	t.Logf("Filtered HTML: %d bytes", len(html))
}

func contains(s, substr string) bool {
	return bytes.Contains([]byte(s), []byte(substr))
}
