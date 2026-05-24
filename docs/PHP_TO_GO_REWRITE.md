# Rewrite PHP Endpoint to Go - Implementation Plan

## Current State Analysis

### What Go Already Has ✅

1. **JSON Parsing** (`dto.go`)
   - `ParseSlikJSON()` - Parse SLIK JSON data
   - Complete data structures (JsonDto, KreditPembiayaan, etc.)
   - Windows-1252 fallback support
   
2. **Data Formatting** (`formatter.go`)
   - Format SLIK data for display
   - Date/time formatting
   - Rupiah formatting
   - Data extraction logic

3. **PDF Generation** (`pdf_generator.go`)
   - HTML transformation (goquery)
   - wkhtmltopdf execution
   - Error handling

### What's Missing ❌

- HTML template for SLIK report
- Function to generate HTML from parsed JSON

## Performance Analysis

### Current Flow (with PHP)
```
SLIK JSON → HTTP POST to PHP (50-200ms network)
         → PHP parse + generate HTML (1.5-4.3s)
         → HTTP response (50-200ms network)
         → Go transformHTML (60-200ms)
         → wkhtmltopdf (300ms)
         
Total: 2-5 seconds
```

### Proposed Flow (pure Go)
```
SLIK JSON → Go parse (10-50ms)
         → Go generate HTML (50-200ms)
         → Go transformHTML (60-200ms)
         → wkhtmltopdf (300ms)
         
Total: 0.4-0.7 seconds ⚡ 5-10x FASTER!
```

## Benefits

### Performance
- **5-10x faster** (2-5s → 0.4-0.7s)
- Helps **ALL requests** (not just cache hits)
- Eliminates network latency
- Eliminates PHP processing overhead

### Architecture
- **Single binary** (no PHP dependency)
- **Simpler deployment** (no PHP server needed)
- **Better error handling** (all in Go)
- **Easier debugging** (one language, one codebase)

### Cost
- **No PHP server** needed
- **Lower infrastructure cost**
- **Reduced complexity**

### Maintenance
- **One language** (Go only)
- **Better type safety**
- **Easier refactoring**
- **Better testing**

## Implementation Plan

### Phase 1: Create HTML Template (2-3 hours)

Create `internal/service/slik/html_template.go`:

```go
package slik

import (
    "bytes"
    "html/template"
)

const slikReportTemplate = `<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<style>
body {
    font-family: Arial, sans-serif;
    font-size: 10pt;
    margin: 0;
    padding: 20px;
}
table {
    border-collapse: collapse;
    width: 100%;
    margin-bottom: 15px;
}
td, th {
    border: 1px solid #555;
    padding: 6px;
    font-size: 9pt;
}
th {
    background-color: #f0f0f0;
    font-weight: bold;
    text-align: left;
}
.header {
    margin-bottom: 20px;
}
.header h3 {
    margin: 2px 0;
    font-family: sans-serif;
}
.section-title {
    font-weight: bold;
    margin-top: 15px;
    margin-bottom: 5px;
}
</style>
</head>
<body>
<div class="header">
    <h3>LAPORAN SLIK</h3>
    {{if .Header.KodeReferensiPengguna}}
    <h3>Ref: {{.Header.KodeReferensiPengguna}}</h3>
    {{end}}
    {{if .Header.TanggalPermintaan}}
    <h3>Tanggal: {{.Header.TanggalPermintaan}}</h3>
    {{end}}
</div>

{{if .Individual.DataPokokDebitur}}
{{$debitur := index .Individual.DataPokokDebitur 0}}
<div class="section-title">DATA DEBITUR</div>
<table>
<tr>
    <td width="30%">Nama</td>
    <td>{{$debitur.NamaDebitur}}</td>
</tr>
<tr>
    <td>No. Identitas (KTP)</td>
    <td>{{$debitur.NoIdentitas}}</td>
</tr>
<tr>
    <td>Alamat</td>
    <td>{{$debitur.Alamat}}</td>
</tr>
{{if $debitur.TempatLahir}}
<tr>
    <td>Tempat/Tanggal Lahir</td>
    <td>{{$debitur.TempatLahir}}, {{$debitur.TanggalLahir}}</td>
</tr>
{{end}}
{{if $debitur.PekerjaanKet}}
<tr>
    <td>Pekerjaan</td>
    <td>{{$debitur.PekerjaanKet}}</td>
</tr>
{{end}}
</table>
{{end}}

{{if .Individual.RingkasanFasilitas}}
{{$ringkasan := .Individual.RingkasanFasilitas}}
<div class="section-title">RINGKASAN FASILITAS</div>
<table>
<tr>
    <td width="30%">Kualitas Terburuk</td>
    <td>{{$ringkasan.KualitasTerburuk}}</td>
</tr>
<tr>
    <td>Bulan Data Terburuk</td>
    <td>{{$ringkasan.KualitasBulanDataTerburuk}}</td>
</tr>
<tr>
    <td>Plafon Efektif Total</td>
    <td>Rp {{$ringkasan.PlafonEfektifTotal}}</td>
</tr>
<tr>
    <td>Baki Debet Total</td>
    <td>Rp {{$ringkasan.BakiDebetTotal}}</td>
</tr>
</table>
{{end}}

{{if .Individual.Fasilitas}}
{{if .Individual.Fasilitas.KreditPembiayan}}
<div class="section-title">DAFTAR FASILITAS KREDIT ({{len .Individual.Fasilitas.KreditPembiayan}})</div>
<table>
<thead>
<tr>
    <th>No</th>
    <th>Lembaga</th>
    <th>Cabang</th>
    <th>Plafon Awal</th>
    <th>Baki Debet</th>
    <th>Kondisi</th>
    <th>Kualitas</th>
    <th>Tanggal Akad</th>
    <th>Jatuh Tempo</th>
</tr>
</thead>
<tbody>
{{range $index, $kredit := .Individual.Fasilitas.KreditPembiayan}}
{{if or (not $.FasilitasAktif) (eq $kredit.KondisiKet "Aktif")}}
<tr>
    <td>{{add $index 1}}</td>
    <td>{{$kredit.LjkKet}}</td>
    <td>{{$kredit.CabangKet}}</td>
    <td>Rp {{$kredit.PlafonAwal}}</td>
    <td>Rp {{$kredit.BakiDebet}}</td>
    <td>{{$kredit.KondisiKet}}</td>
    <td>{{$kredit.KualitasKet}}</td>
    <td>{{$kredit.TanggalAkadAwal}}</td>
    <td>{{$kredit.TanggalJatuhTempo}}</td>
</tr>
{{end}}
{{end}}
</tbody>
</table>
{{end}}
{{end}}

</body>
</html>`

var tmpl = template.Must(template.New("slik").Funcs(template.FuncMap{
    "add": func(a, b int) int { return a + b },
}).Parse(slikReportTemplate))

// GenerateHTMLReport generates HTML report from SLIK JSON data
func GenerateHTMLReport(slikData []byte, fasilitasAktif bool) (string, error) {
    // Parse SLIK JSON
    dto, err := ParseSlikJSON(slikData)
    if err != nil {
        return "", fmt.Errorf("parse slik json: %w", err)
    }
    
    // Prepare template data
    data := struct {
        *JsonDto
        FasilitasAktif bool
    }{
        JsonDto:        dto,
        FasilitasAktif: fasilitasAktif,
    }
    
    // Execute template
    var buf bytes.Buffer
    if err := tmpl.Execute(&buf, data); err != nil {
        return "", fmt.Errorf("execute template: %w", err)
    }
    
    return buf.String(), nil
}
```

### Phase 2: Update PDFGenerator (1-2 hours)

Modify `internal/service/slik/pdf_generator.go`:

```go
// Generate eksekusi pipeline lengkap. fasilitasAktif=true → hanya fasilitas aktif.
func (g *PDFGenerator) Generate(ctx context.Context, slikData []byte, fasilitasAktif bool) ([]byte, error) {
    if len(slikData) == 0 {
        return nil, errors.New("empty slik data")
    }
    
    // OPTION 1: Use Go HTML generation (RECOMMENDED)
    htmlContent, err := GenerateHTMLReport(slikData, fasilitasAktif)
    if err != nil {
        return nil, fmt.Errorf("generate html: %w", err)
    }
    
    // OPTION 2: Fallback to PHP if needed (for gradual migration)
    // htmlContent, err := g.fetchHTML(ctx, slikData, fasilitasAktif)
    // if err != nil {
    //     return nil, fmt.Errorf("fetch html: %w", err)
    // }
    
    if strings.TrimSpace(htmlContent) == "" {
        return nil, errors.New("empty html from generator")
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

// fetchHTML can be kept for backward compatibility or removed entirely
```

### Phase 3: Testing (1-2 hours)

Create `internal/service/slik/html_template_test.go`:

```go
package slik

import (
    "os"
    "testing"
)

func TestGenerateHTMLReport(t *testing.T) {
    // Load sample SLIK JSON
    data, err := os.ReadFile("testdata/sample_slik.json")
    if err != nil {
        t.Fatalf("read sample: %v", err)
    }
    
    // Generate HTML
    html, err := GenerateHTMLReport(data, false)
    if err != nil {
        t.Fatalf("generate html: %v", err)
    }
    
    // Verify HTML contains expected elements
    if !strings.Contains(html, "<!DOCTYPE html>") {
        t.Error("missing DOCTYPE")
    }
    if !strings.Contains(html, "LAPORAN SLIK") {
        t.Error("missing title")
    }
    
    // Optional: save for manual inspection
    os.WriteFile("testdata/output.html", []byte(html), 0644)
}

func TestGenerateHTMLReportFasilitasAktif(t *testing.T) {
    data, err := os.ReadFile("testdata/sample_slik.json")
    if err != nil {
        t.Fatalf("read sample: %v", err)
    }
    
    html, err := GenerateHTMLReport(data, true)
    if err != nil {
        t.Fatalf("generate html: %v", err)
    }
    
    // Verify only active facilities are included
    // (implementation-specific checks)
}

func BenchmarkGenerateHTMLReport(b *testing.B) {
    data, _ := os.ReadFile("testdata/sample_slik.json")
    
    b.ResetTimer()
    for i := 0; i < b.N; i++ {
        _, _ = GenerateHTMLReport(data, false)
    }
}
```

### Phase 4: Deployment (1 hour)

1. **Feature flag** (optional, for gradual rollout):
```go
// config.go
type SLIKConfig struct {
    PDFEndpointURL string // Keep for backward compat
    UseGoGenerator bool   // New flag
    // ...
}

// pdf_generator.go
func (g *PDFGenerator) Generate(ctx context.Context, slikData []byte, fasilitasAktif bool) ([]byte, error) {
    var htmlContent string
    var err error
    
    if g.UseGoGenerator {
        htmlContent, err = GenerateHTMLReport(slikData, fasilitasAktif)
    } else {
        htmlContent, err = g.fetchHTML(ctx, slikData, fasilitasAktif)
    }
    
    // ... rest of pipeline
}
```

2. **Environment variable**:
```env
SLIK_USE_GO_GENERATOR=true  # Enable Go generator
```

3. **Gradual rollout**:
   - Week 1: Deploy with flag=false (PHP still used)
   - Week 2: Enable for 10% of requests
   - Week 3: Enable for 50% of requests
   - Week 4: Enable for 100% of requests
   - Week 5: Remove PHP endpoint dependency

## Expected Results

### Performance
- **Before:** 2-5s average
- **After:** 0.4-0.7s average
- **Improvement:** 5-10x faster (80-85% reduction)

### Metrics
```
P50 latency: 2.5s → 0.5s (80% improvement)
P95 latency: 4.5s → 0.7s (84% improvement)
P99 latency: 5.0s → 0.8s (84% improvement)
```

### Infrastructure
- **Remove:** PHP server (save $X/month)
- **Simplify:** Single Go binary deployment
- **Reduce:** Network calls (0 external dependencies)

## Comparison: Go Rewrite vs Caching

| Metric | Caching | Go Rewrite | Winner |
|--------|---------|------------|--------|
| **Cache hits** | 50-100ms | 400-700ms | Caching |
| **Cache misses** | 2-5s | 400-700ms | **Go Rewrite** |
| **Average (60% hit rate)** | 1.2s | 400-700ms | **Go Rewrite** |
| **Average (80% hit rate)** | 0.7s | 400-700ms | **Go Rewrite** |
| **Complexity** | High (cache invalidation) | Low (direct call) | **Go Rewrite** |
| **Infrastructure** | Need cache storage | None | **Go Rewrite** |
| **Maintenance** | Cache warming, TTL | None | **Go Rewrite** |

**Conclusion:** Go rewrite is better than caching!

## Effort Estimate

| Phase | Time | Complexity |
|-------|------|------------|
| HTML Template | 2-3 hours | Medium |
| Integration | 1-2 hours | Low |
| Testing | 1-2 hours | Low |
| Deployment | 1 hour | Low |
| **Total** | **5-8 hours** | **Medium** |

**ROI:** Extremely high - one day of work for 5-10x performance improvement

## Risks & Mitigation

### Risk 1: HTML output differs from PHP
**Mitigation:** 
- Compare PHP vs Go HTML output
- Visual inspection of generated PDFs
- A/B testing with feature flag

### Risk 2: Missing edge cases
**Mitigation:**
- Test with real SLIK data samples
- Gradual rollout with monitoring
- Keep PHP endpoint as fallback initially

### Risk 3: Template bugs
**Mitigation:**
- Comprehensive unit tests
- Integration tests with wkhtmltopdf
- Manual QA with sample reports

## Recommendation

**IMPLEMENT THIS IMMEDIATELY**

This is a **HIGH IMPACT, MEDIUM EFFORT** optimization that:
- ✅ 5-10x performance improvement
- ✅ Simpler architecture
- ✅ Lower infrastructure cost
- ✅ Better than caching
- ✅ Helps ALL requests (not just cache hits)
- ✅ One day of work

**Priority:** HIGHEST (even higher than caching)

**Timeline:** 
- Week 1: Implementation + testing
- Week 2: Gradual rollout
- Week 3: Full deployment
- Week 4: Remove PHP dependency

**Expected impact:**
- User satisfaction: ⬆️ (faster PDF generation)
- Infrastructure cost: ⬇️ (no PHP server)
- Maintenance burden: ⬇️ (one language)
- Performance: ⬆️⬆️⬆️ (5-10x faster)
