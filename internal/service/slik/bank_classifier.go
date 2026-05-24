package slik

import (
	"strconv"
	"strings"
)

// BankType represents the type of financial institution
type BankType int

const (
	BankTypeUmum BankType = iota
	BankTypeBPR
	BankTypeLembagaPembiayaan
	BankTypeLainnya
)

// ClassifyBank determines the bank type from LJK name
func ClassifyBank(ljkName string) BankType {
	upper := strings.ToUpper(ljkName)

	// Check for BPR/BPRS first (more specific)
	if strings.Contains(upper, "BPR") || strings.Contains(upper, "BPRS") {
		return BankTypeBPR
	}

	// Check for Bank Umum
	if strings.Contains(upper, "BANK") {
		return BankTypeUmum
	}

	// Check for Lembaga Pembiayaan
	if strings.Contains(upper, "FINANCE") ||
	   strings.Contains(upper, "PEMBIAYAAN") ||
	   strings.Contains(upper, "LEASING") ||
	   strings.Contains(upper, "MULTIFINANCE") {
		return BankTypeLembagaPembiayaan
	}

	return BankTypeLainnya
}

// CountBankTypes counts facilities by bank type
func CountBankTypes(facilities []KreditPembiayaan) (umum, bpr, lembaga, lainnya int) {
	for _, f := range facilities {
		switch ClassifyBank(f.LjkKet) {
		case BankTypeUmum:
			umum++
		case BankTypeBPR:
			bpr++
		case BankTypeLembagaPembiayaan:
			lembaga++
		case BankTypeLainnya:
			lainnya++
		}
	}
	return
}

// GetWorstQualityPerType calculates the worst quality for each bank type
func GetWorstQualityPerType(facilities []KreditPembiayaan) (umum, bpr, lainnya string) {
	var maxUmum, maxBpr, maxLainnya int
	for _, f := range facilities {
		q, _ := strconv.Atoi(f.Kualitas)
		switch ClassifyBank(f.LjkKet) {
		case BankTypeUmum:
			if q > maxUmum {
				maxUmum = q
			}
		case BankTypeBPR:
			if q > maxBpr {
				maxBpr = q
			}
		case BankTypeLembagaPembiayaan, BankTypeLainnya:
			if q > maxLainnya {
				maxLainnya = q
			}
		}
	}


	if maxUmum > 0 {
		umum = strconv.Itoa(maxUmum)
	}
	if maxBpr > 0 {
		bpr = strconv.Itoa(maxBpr)
	}
	if maxLainnya > 0 {
		lainnya = strconv.Itoa(maxLainnya)
	}
	return
}

// GetLatestMonth extracts the latest month from tahunBulan fields
func GetLatestMonth(tahunBulan map[string]string) string {
	// Find the highest numbered tahunBulanNN field that has a value
	for i := 1; i <= 24; i++ {
		key := formatTahunBulanKey(i)
		if val, ok := tahunBulan[key]; ok && val != "" && len(val) >= 6 {
			// Return formatted month (MM-YY)
			year := val[2:4]
			month := val[4:6]
			return month + "-" + year
		}
	}
	return "-"
}

func formatTahunBulanKey(n int) string {
	if n < 10 {
		return "tahunBulan0" + string(rune('0'+n))
	}
	return "tahunBulan" + string(rune('0'+n/10)) + string(rune('0'+n%10))
}
