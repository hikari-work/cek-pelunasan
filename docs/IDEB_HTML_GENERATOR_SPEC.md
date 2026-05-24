# Spesifikasi IDEB HTML Generator

## Overview
Endpoint PHP `generate.php` menerima file JSON SLIK (dengan extension `.txt`) dan menghasilkan HTML report "Resume Informasi Debitur (iDeb)".

## Input

### File Upload
- **Form field**: `fileToUpload`
- **File type**: `.txt` (berisi JSON)
- **Filename pattern**: `YYYY_MM_txt_KTP_{NIK}.txt` (contoh: `2026_05_txt_KTP_3175042206680015.txt`)
- **Content**: JSON dengan struktur sesuai `JsonDto` (lihat `internal/service/slik/dto.go`)

### Parameters
- **fasilitasAktif**: `"y"` atau `"n"`
  - `"n"` = tampilkan semua fasilitas
  - `"y"` = hanya tampilkan fasilitas aktif (filter berdasarkan kondisi)

## Output HTML Structure

### 1. Document Head
```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>{kodeReferensiPengguna}</title>
</head>
```

### 2. CSS Styles
- Table: width 100%, border-collapse
- th, td: border 1px solid #dddddd, padding 1px
- th: background #f2f2f2
- .bpr: background #EBF5FB
- .right-image: absolute positioning, top-right corner

### 3. Header Section
```html
<h3>Resume Informasi Debitur (iDeb)</h3>
<img src="logo.png" alt="Logo BSY" class="right-image" width="160">
<h3>Perorangan</h3>
```

### 4. Debitur Info (Flex Layout)
**Left side:**
- ID: `{noIdentitas}` (16 digit KTP)
- Nama: `{namaDebitur}`
- Alamat: `{alamat}`

**Right side:** Signature grid (3x2)
- Row 1: Petugas | Pemeriksa | Pimpinan
- Row 2: Empty cells (40px height) untuk tanda tangan

### 5. Kredit/Pembiayaan Table

**Headers:**
| # | LJK | Realisasi | Jth. Tempo | Suku Bunga (2 cols) | Plafon Awal | Baki Debet | Tgk. Bunga | Tgk. Pokok | Kualitas | Jns. Penggunaan | Sektor Ekonomi | Kondisi | Restruk. |

**Data Rows:**
- Loop through `individual.fasilitas.kreditPembiayan[]`
- Filter jika `fasilitasAktif=y` (hanya yang kondisi aktif)

**Total Row:**
- Colspan 6: "Total"
- Sum of Plafon Awal
- Sum of Baki Debet
- Sum of Tgk. Bunga
- Sum of Tgk. Pokok

**Font size:** 70%

### 6. Riwayat Kualitas Table

**Structure:**
- Header row 1: # | Bulan Data | Kualitas/Hari Tunggakan (colspan 48)
- Header row 2: Month labels (04-26, 03-26, etc.) each colspan 2
- Data rows: Loop through facilities, show tahunBulanNN data

**Columns per month:**
- Col 1: Kualitas (tahunBulanNNKol)
- Col 2: Hari Tunggakan (tahunBulanNNHt)

**Font size:** 60%

### 7. Footer
```
Tanggal Permintaan {tanggalPermintaan formatted}, 
Kode Ref. Pengguna {kodeReferensiPengguna}, 
Tujuan Penggunaan {}, 
Petugas Permintaan {}
```

### 8. Print Button Section
```html
<div class="text-right">
    <button id="print">Cetak</button>
    <a href="index.php"><button>Kembali</button></a>
</div>
```

## Data Formatting Rules

### Dates
- **Input format**: `YYYYMMDD` (8 digits)
- **Output format**: `DD/MM/YYYY`
- **Example**: `20270101` → `01/01/2027`
- **Empty/invalid**: Show `//`

### DateTime
- **Input format**: `YYYYMMDDHHmmss` (14 digits)
- **Output format**: `DD/MM/YYYY HH:mm`
- **Example**: `20260524143000` → `24/05/2026 14:30`

### Numbers (Currency)
- **Input format**: String number `"50000000"`
- **Output format**: Indonesian format with dots `50.000.000`
- **No currency symbol** in table cells
- **Alignment**: right-aligned

### Month Labels
- **Input format**: `YYYYMM` (6 digits)
- **Output format**: `MM-YY`
- **Example**: `202604` → `04-26`

### Interest Rate
- **Format**: `0,00` (Indonesian decimal with comma)
- **Alignment**: right-aligned

## Business Logic

### Facility Filtering (fasilitasAktif=y)
- Filter `kreditPembiayan` array
- Only include facilities where `kondisiKet` indicates active status
- Recalculate totals based on filtered facilities

### History Table Generation
- Show up to 24 months (tahunBulan01 through tahunBulan24)
- Each facility gets one row
- Columns are dynamically generated based on available tahunBulan data
- Empty cells for missing data

### Totals Calculation
- Sum all numeric fields: plafonAwal, bakiDebet, tunggakan bunga, tunggakan pokok
- Format with Indonesian number format

## Validation

### File Validation
PHP endpoint validates:
1. File extension must be `.txt`
2. File must be from "iDeb Viewer" application (validation logic unknown)
3. Content must be valid JSON

Error message if validation fails:
```
Mohon maaf hanya file (.txt) hasil export dari aplikasi iDeb Viewer yang diperbolehkan.
```

## Notes

1. **Logo**: PHP uses `logo.png` (relative path). Go implementation should use configurable logo URL.

2. **Print functionality**: PHP uses jQuery PrintArea plugin. Go implementation doesn't need this (handled by wkhtmltopdf).

3. **Editable fields**: Some cells have `contenteditable="true"` - not needed for PDF generation.

4. **BPR class**: `.bpr` CSS class for highlighting BPR facilities (background color #EBF5FB).

5. **Script tags**: PHP includes jQuery and PrintArea scripts at the end. Go implementation should omit these.

## Implementation Priority

1. ✅ Parse JSON input (already done in `dto.go`)
2. ⏳ Generate HTML from DTO
3. ⏳ Implement formatting functions (dates, numbers)
4. ⏳ Implement filtering logic (fasilitasAktif)
5. ⏳ Create HTTP endpoint handler
6. ⏳ Integration testing with existing PDF pipeline
