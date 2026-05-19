package slik

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"mime/multipart"
	"net/http"
	"os/exec"
	"strings"
	"time"

	"github.com/PuerkitoBio/goquery"
	"golang.org/x/net/html"
)

// PDFGenerator pipeline generate PDF SLIK:
//  1. Kirim file SLIK ke endpoint PHP yang menghasilkan HTML laporan
//  2. Manipulasi HTML (logo, layout tanda tangan, dll) pakai goquery
//  3. Render HTML jadi PDF lewat wkhtmltopdf binary (stdin → stdout)
//
// Binary wkhtmltopdf wajib tersedia di PATH. Di Docker, install via apk/apt.
type PDFGenerator struct {
	HTTPClient    *http.Client
	EndpointURL   string
	LogoURL       string
	WkhtmltopdfBin string // default "wkhtmltopdf"
	UserAgent     string // default Mozilla
	Timeout       time.Duration
}

// NewPDFGenerator builder dengan default sesuai legacy.
func NewPDFGenerator(endpointURL, logoURL string) *PDFGenerator {
	return &PDFGenerator{
		HTTPClient:    &http.Client{Timeout: 60 * time.Second},
		EndpointURL:   endpointURL,
		LogoURL:       logoURL,
		WkhtmltopdfBin: "wkhtmltopdf",
		UserAgent:     "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
		Timeout:       60 * time.Second,
	}
}

// Generate eksekusi pipeline lengkap. fasilitasAktif=true → hanya fasilitas aktif.
func (g *PDFGenerator) Generate(ctx context.Context, slikData []byte, fasilitasAktif bool) ([]byte, error) {
	if len(slikData) == 0 {
		return nil, errors.New("empty slik data")
	}
	htmlContent, err := g.fetchHTML(ctx, slikData, fasilitasAktif)
	if err != nil {
		return nil, fmt.Errorf("fetch html: %w", err)
	}
	if strings.TrimSpace(htmlContent) == "" {
		return nil, errors.New("empty html from endpoint")
	}
	transformed, err := g.transformHTML(htmlContent)
	if err != nil {
		return nil, fmt.Errorf("transform html: %w", err)
	}
	pdf, err := g.renderPDF(ctx, transformed)
	if err != nil {
		return nil, fmt.Errorf("render pdf: %w", err)
	}
	return pdf, nil
}

func (g *PDFGenerator) fetchHTML(ctx context.Context, body []byte, fasilitasAktif bool) (string, error) {
	var buf bytes.Buffer
	mw := multipart.NewWriter(&buf)

	part, err := mw.CreateFormFile("fileToUpload", "ideb.txt")
	if err != nil {
		return "", err
	}
	if _, err := part.Write(body); err != nil {
		return "", err
	}
	val := "n"
	if fasilitasAktif {
		val = "y"
	}
	if err := mw.WriteField("fasilitasAktif", val); err != nil {
		return "", err
	}
	if err := mw.Close(); err != nil {
		return "", err
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, g.EndpointURL, &buf)
	if err != nil {
		return "", err
	}
	req.Header.Set("Content-Type", mw.FormDataContentType())
	req.Header.Set("User-Agent", g.UserAgent)

	resp, err := g.HTTPClient.Do(req)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()
	if resp.StatusCode/100 != 2 {
		return "", fmt.Errorf("endpoint returned %s", resp.Status)
	}
	out, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", err
	}
	return string(out), nil
}

func (g *PDFGenerator) transformHTML(htmlContent string) (string, error) {
	doc, err := goquery.NewDocumentFromReader(strings.NewReader(htmlContent))
	if err != nil {
		return "", err
	}

	// Hapus tag script.
	doc.Find("script").Remove()

	// Hapus comment node — wkhtmltopdf umumnya bisa handle, tapi ikut legacy untuk konsistensi.
	removeCommentNodes(doc)

	// Pindahkan <style> dari body ke head.
	doc.Find("body style").Each(func(_ int, s *goquery.Selection) {
		clone := s.Clone()
		s.Remove()
		doc.Find("head").AppendSelection(clone)
	})

	// Insert images: logo perusahaan + header tabel 2 kolom.
	g.insertHeaderImage(doc)

	// Hapus tombol cetak.
	doc.Find("div.text-right").Each(func(_ int, s *goquery.Selection) {
		if s.Find("button#print").Length() > 0 {
			s.Remove()
		}
	})

	// Fix layout grid tanda tangan jadi tabel.
	fixSignatureGrid(doc)

	// Inject @page A4 landscape + border tabel default.
	doc.Find("head").AppendHtml(`<style>
@page { size: A4 landscape; margin: 15mm; }
table:not(.slik-header) { border-collapse: collapse; }
table:not(.slik-header) td, table:not(.slik-header) th { border: 1px solid #555; padding: 2px 5px; }
.slik-header, .slik-header td, .slik-header th { border: none !important; }
</style>`)

	out, err := doc.Html()
	if err != nil {
		return "", err
	}
	return out, nil
}

func removeCommentNodes(doc *goquery.Document) {
	var rec func(*html.Node)
	rec = func(n *html.Node) {
		// kumpulkan dulu untuk hindari modifikasi saat traversing.
		var toRemove []*html.Node
		for c := n.FirstChild; c != nil; c = c.NextSibling {
			if c.Type == html.CommentNode {
				toRemove = append(toRemove, c)
			} else {
				rec(c)
			}
		}
		for _, c := range toRemove {
			n.RemoveChild(c)
		}
	}
	for _, n := range doc.Nodes {
		rec(n)
	}
}

func (g *PDFGenerator) insertHeaderImage(doc *goquery.Document) {
	img := doc.Find("img.right-image").First()
	if img.Length() == 0 {
		return
	}
	img.SetAttr("src", g.LogoURL)
	img.RemoveAttr("style")
	img.SetAttr("style", "width: 160px;")

	// Bangun tabel header.
	headerHTML := `<table class="slik-header" style="width: 100%; border: none; margin-bottom: 20px; border-collapse: collapse;"><tr><td style="border: none; vertical-align: middle; text-align: left;" id="slik-header-left"></td><td style="border: none; vertical-align: middle; text-align: right; width: 1%; white-space: nowrap;" id="slik-header-right"></td></tr></table>`

	body := doc.Find("body").First()
	body.PrependHtml(headerHTML)

	// Pindahkan h3 ke kiri.
	leftCell := doc.Find("#slik-header-left").First()
	doc.Find("h3").Each(func(_ int, h *goquery.Selection) {
		h.SetAttr("style", "margin: 2px 0; font-family: sans-serif;")
		clone := h.Clone()
		h.Remove()
		leftCell.AppendSelection(clone)
	})

	// Pindahkan logo ke kanan.
	rightCell := doc.Find("#slik-header-right").First()
	imgClone := img.Clone()
	img.Remove()
	rightCell.AppendSelection(imgClone)

	// Bersihkan id helper supaya tidak nyangkut di output.
	leftCell.RemoveAttr("id")
	rightCell.RemoveAttr("id")
}

func fixSignatureGrid(doc *goquery.Document) {
	gridDiv := doc.Find("div").FilterFunction(func(_ int, s *goquery.Selection) bool {
		style, ok := s.Attr("style")
		return ok && strings.Contains(style, "display: grid")
	}).First()
	if gridDiv.Length() == 0 {
		return
	}

	children := gridDiv.Children()
	tableHTML := strings.Builder{}
	tableHTML.WriteString(`<table style="width: 300px; border-collapse: collapse; font-family: sans-serif; font-size: 12px; border: 0.5px solid blue; margin-top: 50px; page-break-inside: avoid;"><tbody><tr>`)
	limit := children.Length()
	if limit > 6 {
		limit = 6
	}
	for i := 0; i < 3 && i < limit; i++ {
		text := strings.TrimSpace(children.Eq(i).Text())
		fmt.Fprintf(&tableHTML, `<td style="border: 0.5px solid blue; font-weight: bold; color: blue; text-align: center; padding: 4px; width: 100px;">%s</td>`, html.EscapeString(text))
	}
	tableHTML.WriteString(`</tr><tr>`)
	for i := 3; i < 6 && i < limit; i++ {
		tableHTML.WriteString(`<td style="border: 0.5px solid blue; height: 40px;"></td>`)
	}
	tableHTML.WriteString(`</tr></tbody></table>`)

	container := doc.Find("div.printableArea").First()
	if container.Length() == 0 {
		container = doc.Find("body").First()
	}
	container.AppendHtml(tableHTML.String())
	gridDiv.Remove()

	// Cleanup parent flex.
	parentFlex := doc.Find("div").FilterFunction(func(_ int, s *goquery.Selection) bool {
		style, ok := s.Attr("style")
		return ok && strings.Contains(style, "display: flex")
	}).First()
	if parentFlex.Length() > 0 && parentFlex.Children().Length() <= 1 {
		parentFlex.SetAttr("style", "font-family: sans-serif;")
	}
}

func (g *PDFGenerator) renderPDF(ctx context.Context, htmlContent string) ([]byte, error) {
	bin := g.WkhtmltopdfBin
	if bin == "" {
		bin = "wkhtmltopdf"
	}
	cctx, cancel := context.WithTimeout(ctx, g.Timeout)
	defer cancel()

	args := []string{
		"--quiet",
		"--enable-local-file-access",
		"--page-size", "A4",
		"--orientation", "Landscape",
		"--margin-top", "15",
		"--margin-bottom", "15",
		"--margin-left", "15",
		"--margin-right", "15",
		"-", "-", // stdin → stdout
	}
	cmd := exec.CommandContext(cctx, bin, args...)
	cmd.Stdin = strings.NewReader(htmlContent)

	var stdout, stderr bytes.Buffer
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr

	if err := cmd.Run(); err != nil {
		// wkhtmltopdf kadang exit 1 walau output PDF valid (warning soal asset eksternal).
		// Anggap sukses kalau ada output PDF dengan magic bytes "%PDF".
		if stdout.Len() > 4 && bytes.HasPrefix(stdout.Bytes(), []byte("%PDF")) {
			slog.Warn("wkhtmltopdf returned non-zero but produced PDF",
				"err", err, "stderr", stderr.String())
			return stdout.Bytes(), nil
		}
		return nil, fmt.Errorf("wkhtmltopdf: %w (stderr=%s)", err, strings.TrimSpace(stderr.String()))
	}
	if stdout.Len() == 0 {
		return nil, errors.New("wkhtmltopdf produced empty output")
	}
	return stdout.Bytes(), nil
}
