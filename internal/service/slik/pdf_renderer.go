package slik

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"os/exec"
	"strings"
	"time"
)

// RenderHTMLToPDF converts HTML string to PDF using wkhtmltopdf binary.
// This is a standalone function that can be used without PDFGenerator.
func RenderHTMLToPDF(ctx context.Context, htmlContent string, timeout time.Duration) ([]byte, error) {
	if timeout <= 0 {
		timeout = 60 * time.Second
	}

	cctx, cancel := context.WithTimeout(ctx, timeout)
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

	cmd := exec.CommandContext(cctx, "wkhtmltopdf", args...)
	cmd.Stdin = strings.NewReader(htmlContent)

	var stdout, stderr bytes.Buffer
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr

	if err := cmd.Run(); err != nil {
		// wkhtmltopdf kadang exit 1 walau output PDF valid (warning soal asset eksternal).
		// Anggap sukses kalau ada output PDF dengan magic bytes "%PDF".
		if stdout.Len() > 4 && bytes.HasPrefix(stdout.Bytes(), []byte("%PDF")) {
			return stdout.Bytes(), nil
		}
		return nil, fmt.Errorf("wkhtmltopdf: %w (stderr=%s)", err, strings.TrimSpace(stderr.String()))
	}

	if stdout.Len() == 0 {
		return nil, errors.New("wkhtmltopdf produced empty output")
	}

	return stdout.Bytes(), nil
}
