# Optimasi Project Cek-Pelunasan

Dokumentasi optimasi yang telah dilakukan pada project ini.

## 1. MongoDB Connection Pooling ✅

**File:** `internal/repository/mongo.go`

**Perubahan:**
```go
SetMaxPoolSize(40)           // Max 40 concurrent connections
SetMinPoolSize(5)            // Keep 5 idle connections
SetMaxConnIdleTime(30s)      // Close idle connections after 30s
```

**Alasan:**
- Server prod memiliki RAM terbatas (~1GB total, ~400MB available)
- MongoDB container sudah menggunakan 219MB
- App saat ini hanya menggunakan ~42MB
- Setting konservatif untuk menghindari memory pressure

**Estimasi Memory Impact:**
- Setiap connection MongoDB: ~1-2MB
- Max overhead: 40-80MB (masih aman untuk server 1GB)
- Idle connections minimal untuk mengurangi footprint

**Monitoring:**
```bash
# Check memory usage
ssh prod "free -h && ps aux | grep cek-pelunasan"

# Check MongoDB connections
ssh prod "docker exec mongodb mongo --eval 'db.serverStatus().connections'"
```

## 2. Binary Size Optimization ✅

**File:** `Makefile`

**Perubahan:**
```makefile
go build -trimpath -ldflags="-s -w" -o $(BIN) $(PKG)
```

**Hasil:**
- **Before:** 47MB
- **After:** 32MB
- **Reduction:** 32% (15MB saved)

**Flags Explanation:**
- `-trimpath`: Remove file system paths from binary (security + size)
- `-ldflags="-s -w"`: Strip debug info and symbol table
  - `-s`: Omit symbol table
  - `-w`: Omit DWARF debug info

**Further Optimization (Optional):**
```bash
# Install UPX for additional compression
sudo pacman -S upx  # or apt install upx-ucl

# Compress binary (can reduce to ~10-15MB)
upx --best --lzma bin/cekpelunasan
```

## 3. Code Formatting ✅

**Fixed:** 19 files dengan formatting issues

**Command:**
```bash
gofmt -s -w ./internal ./cmd
```

**Impact:**
- Consistent code style
- Simplified syntax where possible
- Better readability

## 4. Build Process Improvement ✅

**Makefile Updates:**
- Added optimization flags to build target
- Added file size display after build
- Maintained backward compatibility

## Performance Metrics

### Server Specifications (Production)
```
RAM:        973MB total (391MB available)
CPU:        1 core
MongoDB:    219MB (container limit 350MB)
App:        42MB baseline memory
Swap:       2GB (330MB used)
```

### Connection Pool Sizing Formula
```
MaxPoolSize = min(RAM_available_MB / 10, 100)
            = min(400 / 10, 100)
            = 40 connections

MinPoolSize = MaxPoolSize * 0.125
            = 40 * 0.125
            = 5 connections
```

## Recommendations for Future

### High Priority
1. **Add Integration Tests** - Only 15% test coverage
2. **Implement Caching** - Redis or in-memory for frequent queries
3. **Add Request Timeouts** - All external calls (MongoDB, HTTP, R2)

### Medium Priority
4. **Context Propagation** - Replace 12 instances of `context.TODO()`
5. **Concurrent Operations** - Parallelize I/O in large handlers
6. **Metrics Dashboard** - Grafana for `/actuator/prometheus`

### Low Priority
7. **Config Hot-Reload** - For development convenience
8. **Structured Tracing** - Add trace IDs for request tracking
9. **Docker Image** - Consider distroless (blocked by wkhtmltopdf dependency)

## Deployment Checklist

Before deploying optimized version:

- [ ] Test locally with `make build && make run`
- [ ] Verify MongoDB connection pool with load testing
- [ ] Monitor memory usage on staging
- [ ] Check application logs for connection errors
- [ ] Backup current production binary
- [ ] Deploy during low-traffic window
- [ ] Monitor for 24 hours post-deployment

## Rollback Plan

If issues occur:
```bash
# On production server
cd /root/cek-pelunasan
systemctl stop cek-pelunasan
cp cek-pelunasan.backup cek-pelunasan
systemctl start cek-pelunasan
```

## Monitoring Commands

```bash
# Memory usage
ssh prod "free -h"

# App memory
ssh prod "ps aux | grep cek-pelunasan | grep -v grep"

# MongoDB stats
ssh prod "docker stats mongodb --no-stream"

# Connection count
ssh prod "docker exec mongodb mongo --eval 'db.serverStatus().connections'"

# App logs
ssh prod "journalctl -u cek-pelunasan -f"
```

---

**Last Updated:** 2026-05-23
**Applied By:** Claude Code
**Status:** ✅ Completed
