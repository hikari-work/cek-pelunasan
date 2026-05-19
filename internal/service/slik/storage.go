package slik

import (
	"fmt"
	"time"
)

const (
	subPDF  = "pdf"
	subTXT  = "txt"
	subIDEB = "ideb"
)

// FolderForMonth bangun folder bulan dari yyyymm 6-digit, misal "202605" → "2026_05".
// Mengembalikan empty string kalau input tidak valid.
func FolderForMonth(yyyymm string) string {
	if len(yyyymm) != 6 {
		return ""
	}
	return yyyymm[:4] + "_" + yyyymm[4:]
}

// CurrentFolder pakai zona WIB (UTC+7) sesuai legacy MonthFolderProvider.
func CurrentFolder() string {
	wib := time.FixedZone("WIB", 7*3600)
	now := time.Now().In(wib)
	return fmt.Sprintf("%04d_%02d", now.Year(), int(now.Month()))
}

// PDFKey "{folder}/pdf/{name}".
func PDFKey(folder, name string) string { return folder + "/" + subPDF + "/" + name }

// TXTKey "{folder}/txt/{name}".
func TXTKey(folder, name string) string { return folder + "/" + subTXT + "/" + name }

// IDEBKey "{folder}/ideb/{name}".
func IDEBKey(folder, name string) string { return folder + "/" + subIDEB + "/" + name }

// KTPTextKey path ke KTP txt: "{folder}/txt/KTP_{id}.txt".
func KTPTextKey(folder, ktpID string) string { return TXTKey(folder, "KTP_"+ktpID+".txt") }

// PDFFolderPrefix "{folder}/pdf/" untuk listing.
func PDFFolderPrefix(folder string) string { return folder + "/" + subPDF + "/" }

// SubfolderForExt mapping ext → subfolder.
// Ext lower-case tanpa titik. Kembalikan "" kalau ext tidak didukung.
func SubfolderForExt(ext string) (sub string, contentType string) {
	switch ext {
	case "pdf":
		return subPDF, "application/pdf"
	case "txt":
		return subTXT, "text/plain"
	case "ideb":
		return subIDEB, "application/octet-stream"
	default:
		return "", ""
	}
}
