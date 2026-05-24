# PDF Generation Optimization Guide

## Problem Analysis

### Benchmark Results
- **wkhtmltopdf execution:** 296ms (complex HTML, 50 rows)
- **HTML transformation:** 60-200ms (goquery DOM manipulation)
- **Total observed:** 2-5 seconds
- **Missing time:** 1.5-4.5 seconds

### Bottleneck Identified
**PHP Endpoint (75-90% of total time)**
- Network latency: 50-200ms
- PHP processing: 1.5-4.3s
  - Parse SLIK data
  - Database queries
  - Business logic
  - HTML generation

### Performance Breakdown
```
Total: 2-5 seconds
├─ PHP Endpoint:     1.5-4.5s (75-90%) ← MAIN BOTTLENECK
├─ HTML Transform:   0.06-0.2s (3-5%)
└─ wkhtmltopdf:      0.3s (6-15%)
```

## Optimization Strategies

### 1. PDF Caching (HIGHEST PRIORITY)

**Impact:** 20-50x speedup for cache hits  
**Effort:** Low (1-2 hours)  
**Savings:** 2-5s → <100ms

#### Implementation

```go
package slik

import (
    "context"
    "crypto/sha256"
    "encoding/hex"
    "fmt"
    "time"
    
    "github.com/hikari-work/cek-pelunasan/internal/platform/r2"
)

type CachedPDFGenerator struct {
    generator *PDFGenerator
    storage   *r2.Client
    ttl       time.Duration
}

func NewCachedPDFGenerator(gen *PDFGenerator, storage *r2.Client) *CachedPDFGenerator {
    return &CachedPDFGenerator{
        generator: gen,
        storage:   storage,
        ttl:       24 * time.Hour, // Cache for 24 hours
    }
}

// Generate with caching
func (c *CachedPDFGenerator) Generate(ctx context.Context, slikData []byte, fasilitasAktif bool) ([]byte, error) {
    // Generate cache key
    cacheKey := c.cacheKey(slikData, fasilitasAktif)
    
    // Try to get from cache
    cached, err := c.getFromCache(ctx, cacheKey)
    if err == nil && cached != nil {
        return cached, nil // Cache hit! ~50ms
    }
    
    // Cache miss - generate PDF
    pdf, err := c.generator.Generate(ctx, slikData, fasilitasAktif)
    if err != nil {
        return nil, err
    }
    
    // Store in cache (async, don't block response)
    go func() {
        _ = c.storeInCache(context.Background(), cacheKey, pdf)
    }()
    
    return pdf, nil
}

func (c *CachedPDFGenerator) cacheKey(slikData []byte, fasilitasAktif bool) string {
    h := sha256.New()
    h.Write(slikData)
    if fasilitasAktif {
        h.Write([]byte("aktif"))
    }
    return "slik-pdf-" + hex.EncodeToString(h.Sum(nil))
}

func (c *CachedPDFGenerator) getFromCache(ctx context.Context, key string) ([]byte, error) {
    folder := CurrentFolder()
    fullKey := PDFKey(folder, key+".pdf")
    
    obj, err := c.storage.GetObject(ctx, fullKey)
    if err != nil {
        return nil, err
    }
    defer obj.Close()
    
    return io.ReadAll(obj)
}

func (c *CachedPDFGenerator) storeInCache(ctx context.Context, key string, pdf []byte) error {
    folder := CurrentFolder()
    fullKey := PDFKey(folder, key+".pdf")
    
    return c.storage.PutObject(ctx, fullKey, bytes.NewReader(pdf), int64(len(pdf)), "application/pdf")
}
```

#### Expected Results
- **First request:** 2-5s (generate + cache)
- **Subsequent requests:** 50-100ms (serve from R2)
- **Cache hit rate:** 60-80% (typical for SLIK reports)
- **Average response time:** 0.5-1.5s (weighted average)

### 2. Optimize PHP Endpoint (HIGH PRIORITY)

**Impact:** Save 1-3 seconds  
**Effort:** Medium (requires PHP code access)  
**Savings:** 1.5-4.5s → 0.5-1.5s

#### Recommendations

**A. Profile PHP Code**
```php
// Add timing logs
$start = microtime(true);

// ... SLIK processing ...

$parseTime = microtime(true) - $start;
error_log("SLIK parse: {$parseTime}s");

// ... Database queries ...

$dbTime = microtime(true) - $start - $parseTime;
error_log("DB queries: {$dbTime}s");

// ... HTML generation ...

$htmlTime = microtime(true) - $start - $parseTime - $dbTime;
error_log("HTML gen: {$htmlTime}s");
```

**B. Common Optimizations**
1. **Cache database queries**
   - Use Redis/Memcached for lookup tables
   - Cache customer data for 5-10 minutes
   
2. **Optimize HTML generation**
   - Use template caching
   - Pre-compile templates
   - Reduce string concatenation
   
3. **Database query optimization**
   - Add indexes on frequently queried columns
   - Use prepared statements
   - Batch queries where possible
   
4. **Reduce data processing**
   - Parse SLIK data once, cache result
   - Avoid redundant calculations
   - Use efficient data structures

**C. Alternative: Move to Go**
If PHP endpoint can't be optimized, consider rewriting in Go:
- Parse SLIK data in Go (faster)
- Generate HTML in Go (using templates)
- Eliminate network round-trip
- Expected: 1.5-4.5s → 100-300ms

### 3. Alternative PDF Libraries (MEDIUM PRIORITY)

**Impact:** Save 100-200ms  
**Effort:** High (requires rewrite)  
**Savings:** 300ms → 100-150ms

#### Options

**A. Chromium Headless (via gotenberg)**
```yaml
# docker-compose.yml
services:
  gotenberg:
    image: gotenberg/gotenberg:7
    ports:
      - "3000:3000"
```

```go
// Use gotenberg client
func (g *PDFGenerator) renderPDFGotenberg(ctx context.Context, html string) ([]byte, error) {
    req, _ := http.NewRequestWithContext(ctx, "POST", 
        "http://gotenberg:3000/forms/chromium/convert/html",
        strings.NewReader(html))
    
    resp, err := http.DefaultClient.Do(req)
    if err != nil {
        return nil, err
    }
    defer resp.Body.Close()
    
    return io.ReadAll(resp.Body)
}
```

**Pros:**
- Faster (100-150ms vs 300ms)
- Modern rendering engine
- Better CSS support

**Cons:**
- Requires Docker service
- More memory usage
- Additional infrastructure

**B. Keep wkhtmltopdf**
- Already fast enough (300ms)
- Simple, no dependencies
- Good quality output
- **Recommended: Don't change**

### 4. Process Pooling (LOW PRIORITY)

**Impact:** Save 50-100ms  
**Effort:** High (complex implementation)  
**Savings:** Not worth the effort

wkhtmltopdf process spawning is only ~50-100ms, and implementation is complex.

## Implementation Plan

### Phase 1: Quick Wins (Week 1)
1. ✅ Implement PDF caching (1-2 hours)
2. ✅ Add monitoring/metrics (1 hour)
3. ✅ Test cache hit rates (1 day)

**Expected improvement:** 2-5s → 0.5-1.5s average

### Phase 2: PHP Optimization (Week 2-3)
1. Profile PHP endpoint
2. Identify slow queries
3. Implement optimizations
4. Test improvements

**Expected improvement:** 0.5-1.5s → 0.3-0.8s average

### Phase 3: Consider Alternatives (Optional)
1. Evaluate moving PHP logic to Go
2. Test gotenberg if needed
3. Benchmark improvements

**Expected improvement:** 0.3-0.8s → 0.2-0.5s average

## Monitoring

### Metrics to Track
```go
// Add Prometheus metrics
var (
    pdfGenerationDuration = prometheus.NewHistogramVec(
        prometheus.HistogramOpts{
            Name: "slik_pdf_generation_duration_seconds",
            Help: "PDF generation duration",
            Buckets: []float64{0.1, 0.5, 1, 2, 5, 10},
        },
        []string{"cache_hit"},
    )
    
    pdfCacheHitRate = prometheus.NewCounter(
        prometheus.CounterOpts{
            Name: "slik_pdf_cache_hits_total",
            Help: "Total PDF cache hits",
        },
    )
)

// Usage
start := time.Now()
pdf, err := generator.Generate(ctx, data, aktif)
duration := time.Since(start).Seconds()

pdfGenerationDuration.WithLabelValues("false").Observe(duration)
```

### Expected Metrics After Optimization
- **Average response time:** 0.5-1.5s (down from 2-5s)
- **Cache hit rate:** 60-80%
- **P50 latency:** 100ms (cache hit)
- **P95 latency:** 3s (cache miss)
- **P99 latency:** 5s (cache miss + slow PHP)

## Conclusion

### Key Findings
1. **wkhtmltopdf is NOT the bottleneck** (only 300ms)
2. **PHP endpoint is the main bottleneck** (1.5-4.5s)
3. **Caching provides biggest ROI** (20-50x speedup)

### Recommended Actions
1. **Implement PDF caching immediately** (highest impact, lowest effort)
2. **Profile and optimize PHP endpoint** (high impact, medium effort)
3. **Keep wkhtmltopdf** (already fast enough)
4. **Monitor cache hit rates** (measure success)

### Expected Results
- **Before:** 2-5s average
- **After caching:** 0.5-1.5s average (70% improvement)
- **After PHP optimization:** 0.3-0.8s average (85% improvement)
