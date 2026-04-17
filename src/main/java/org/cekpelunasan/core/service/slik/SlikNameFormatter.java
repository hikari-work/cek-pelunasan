package org.cekpelunasan.core.service.slik;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.core.service.slik.dto.SlikJsonDto;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.*;

/**
 * Mengubah data SLIK mentah (dari JSON) menjadi pesan Telegram yang
 * rapi dan mudah dibaca. Class ini menangani dua hal utama:
 * <ol>
 *   <li>Parsing JSON SLIK menjadi objek {@link SlikJsonDto}</li>
 *   <li>Memformat objek tersebut menjadi {@link TdApi.FormattedText} lengkap
 *       dengan bold, code, italic, dan expandable blockquote untuk setiap
 *       fasilitas kredit</li>
 * </ol>
 *
 * <p>Karena pesan Telegram punya batas karakter, class ini secara otomatis
 * memotong daftar fasilitas kredit jika terlalu panjang, dan menambahkan
 * catatan berapa fasilitas yang tidak ditampilkan.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlikNameFormatter {

    /** Batas karakter konten pesan sebelum footer ditambahkan. */
    private static final int MAX_CONTENT_CHARS = 3800;

    private static final DateTimeFormatter DATETIME_IN  = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter DATE_IN      = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATETIME_OUT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATE_OUT     = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ObjectMapper objectMapper;

    // ─────────────────────────────────────────────
    // Parse
    // ─────────────────────────────────────────────

    /**
     * Mengurai byte array JSON menjadi objek {@link SlikJsonDto}. Pertama
     * dicoba dengan encoding UTF-8. Jika gagal (misalnya file dikirim dengan
     * encoding lama), dicoba ulang dengan Windows-1252 yang umum digunakan
     * pada sistem perbankan lokal.
     *
     * @param jsonBytes isi file JSON dalam bentuk byte array
     * @return {@link Mono} berisi objek DTO, atau kosong jika parsing gagal
     */
    public Mono<SlikJsonDto> parse(byte[] jsonBytes) {
        return Mono.fromCallable(() -> {
                try {
                    return objectMapper.readValue(jsonBytes, SlikJsonDto.class);
                } catch (Exception e) {
                    log.debug("UTF-8 parse failed ({}), retrying as Windows-1252", e.getMessage());
                    String content = new String(jsonBytes, java.nio.charset.Charset.forName("windows-1252"));
                    return objectMapper.readValue(content, SlikJsonDto.class);
                }
            })
            .subscribeOn(Schedulers.boundedElastic())
            .doOnError(e -> log.error("Failed to parse SLIK JSON (size={} bytes): {}", jsonBytes.length, e.getMessage()))
            .onErrorResume(e -> Mono.empty());
    }

    // ─────────────────────────────────────────────
    // Format → TdApi.FormattedText
    // ─────────────────────────────────────────────

    /**
     * Memformat data satu halaman SLIK menjadi {@link TdApi.FormattedText}
     * yang siap dikirim via Telegram. Jika data DTO tersedia, ditampilkan
     * lengkap dengan informasi debitur, ringkasan fasilitas, dan daftar kredit.
     * Jika tidak ada DTO, ditampilkan pesan ringkas berisi nama file dan nomor KTP.
     *
     * @param data    data satu halaman SLIK termasuk DTO dan metadata file
     * @param current indeks halaman saat ini (dimulai dari 0)
     * @param total   total halaman yang tersedia dalam sesi
     * @return pesan terformat siap kirim ke Telegram
     */
    public TdApi.FormattedText format(SlikSessionCache.SlikPageData data, int current, int total) {
        return data.dto() == null
            ? formatNoData(data, current, total)
            : formatFull(data, current, total);
    }

    /**
     * Memformat pesan lengkap ketika data DTO SLIK tersedia. Pesan mencakup
     * header, data debitur, ringkasan fasilitas, dan daftar fasilitas kredit
     * (dalam expandable blockquote). Fasilitas yang tidak muat karena batasan
     * karakter akan ditampilkan sebagai catatan ringkas.
     *
     * @param data    data halaman SLIK
     * @param current indeks halaman saat ini
     * @param total   total halaman
     * @return pesan terformat lengkap
     */
    private TdApi.FormattedText formatFull(SlikSessionCache.SlikPageData data, int current, int total) {
        SlikJsonDto dto = data.dto();
        SlikJsonDto.Individual ind = dto.individual();
        SlikJsonDto.Header hdr = dto.header();

        String namaDebitur = ind.dataPokokDebitur() != null && !ind.dataPokokDebitur().isEmpty()
            ? ind.dataPokokDebitur().getFirst().namaDebitur() : "";

        MessageBuilder mb = new MessageBuilder();

        // ── Header ──────────────────────────────
        mb.bold("📊 HASIL SLIK — " + orDash(namaDebitur)); mb.append("\n");
        mb.append("━━━━━━━━━━━━━━━━━━━━━\n");
        if (hdr != null) {
            mb.append("📋 Ref: "); mb.code(orDash(hdr.kodeReferensiPengguna())); mb.append("\n");
            mb.append("📅 Tanggal: " + formatDateTime(hdr.tanggalPermintaan()) + "\n");
        }

        // ── Debitur ─────────────────────────────
        if (ind.dataPokokDebitur() != null && !ind.dataPokokDebitur().isEmpty()) {
            SlikJsonDto.DataPokokDebitur deb = ind.dataPokokDebitur().getFirst();
            mb.append("\n👤 "); mb.bold("DATA DEBITUR\n");
            mb.append("🪪 KTP: "); mb.code(orDash(deb.noIdentitas())); mb.append("\n");
            mb.append("📍 Alamat: " + orDash(deb.alamat()) + "\n");
            if (isNotBlank(deb.tempatLahir()) || isNotBlank(deb.tanggalLahir())) {
                mb.append("🎂 Lahir: " + orDash(deb.tempatLahir()) + ", " + formatDate(deb.tanggalLahir()) + "\n");
            }
            mb.append("💼 Pekerjaan: " + orDash(deb.pekerjaanKet()) + "\n");
        }

        // ── Ringkasan ────────────────────────────
        if (ind.ringkasanFasilitas() != null) {
            SlikJsonDto.RingkasanFasilitas rf = ind.ringkasanFasilitas();
            mb.append("\n📈 "); mb.bold("RINGKASAN FASILITAS\n");
            mb.append("Kol. Terburuk : "); mb.bold(orDash(rf.kualitasTerburuk())); mb.append("\n");
            mb.append("Bulan Data    : " + formatYearMonth(rf.kualitasBulanDataTerburuk()) + "\n");
            mb.append("Plafon Total  : " + formatRupiah(rf.plafonEfektifTotal()) + "\n");
            mb.append("Baki Debet    : " + formatRupiah(rf.bakiDebetTotal()) + "\n");
        }

        // ── Fasilitas (expandable blockquote per item) ──
        if (ind.fasilitas() != null && ind.fasilitas().kreditPembiayan() != null) {
            List<SlikJsonDto.KreditPembiayaan> list = ind.fasilitas().kreditPembiayan();
            mb.append("\n💳 "); mb.bold("FASILITAS KREDIT (" + list.size() + ")\n");

            int shown = 0;
            for (SlikJsonDto.KreditPembiayaan k : list) {
                String fasText = buildFasilitasText(shown + 1, k);
                // Cek apakah masih muat sebelum footer (~200 chars)
                if (mb.length() + fasText.length() + 200 > MAX_CONTENT_CHARS) {
                    int remaining = list.size() - shown;
                    mb.italic("_(+" + remaining + " fasilitas lainnya — gunakan /slik {ktp} untuk detail)_\n");
                    break;
                }
                int start = mb.markStart();
                mb.append(fasText);
                mb.endExpandableBlockquote(start);
                mb.append("\n");
                shown++;
            }
        }

        // ── Footer (selalu ada, bahkan jika pesan dipotong) ──
        if (isNotBlank(data.idNumber())) {
            mb.append("\n🎯 "); mb.code("/slik " + data.idNumber()); mb.append("\n");
        }
        if (isNotBlank(data.contentKey())) {
            mb.append("📥 "); mb.code("/doc " + data.contentKey()); mb.append("\n");
        }
        mb.italic("📄 Halaman " + (current + 1) + " dari " + total);

        return mb.build();
    }

    /**
     * Membangun teks detail untuk satu fasilitas kredit. Mencakup nama LJK,
     * cabang, plafon, baki debet, kondisi, kolektibilitas, jenis penggunaan,
     * jangka waktu, kolektibilitas terburuk 24 bulan, dan hari tunggakan maksimal.
     *
     * @param index nomor urut fasilitas (dimulai dari 1)
     * @param k     data fasilitas kredit
     * @return string teks detail fasilitas siap ditambahkan ke pesan
     */
    private String buildFasilitasText(int index, SlikJsonDto.KreditPembiayaan k) {
        StringBuilder sb = new StringBuilder();
        sb.append(index).append(". ").append(orDash(k.getLjkKet())).append("\n");
        if (isNotBlank(k.getCabangKet())) sb.append("Cabang    : ").append(k.getCabangKet()).append("\n");
        sb.append("Plafon    : ").append(formatRupiah(k.getPlafonAwal())).append(" | Bakidebet: ").append(formatRupiah(k.getBakiDebet())).append("\n");
        sb.append("Kondisi   : ").append(orDash(k.getKondisiKet())).append(" | Kol: ").append(orDash(k.getKualitasKet())).append("\n");
        if (isNotBlank(k.getJenisPenggunaanKet())) sb.append("Penggunaan: ").append(k.getJenisPenggunaanKet()).append("\n");
        if (isNotBlank(k.getTanggalAkadAwal()) || isNotBlank(k.getTanggalJatuhTempo())) {
            sb.append("Jangka    : ").append(formatDate(k.getTanggalAkadAwal()))
              .append(" → ").append(formatDate(k.getTanggalJatuhTempo())).append("\n");
        }
        String worstKol  = findWorstKol(k.getTahunBulan());
        String maxHt     = findMaxHt(k.getTahunBulan());
        String kolPeriod = findKolPeriodLabel(k.getTahunBulan());
        String kolLabel  = kolPeriod != null ? "Kol Terburuk s.d. " + kolPeriod : "Kol Terburuk";
        if (isNotBlank(worstKol)) sb.append(kolLabel).append(": ").append(worstKol).append("\n");
        if (isNotBlank(maxHt) && !"0".equals(maxHt)) sb.append("Max Hari Tunggakan: ").append(maxHt).append(" hari\n");
        return sb.toString();
    }

    /**
     * Memformat pesan singkat ketika data DTO SLIK tidak tersedia untuk satu
     * file. Tetap menampilkan nama file dan nomor KTP jika bisa diekstrak.
     *
     * @param data    data halaman SLIK tanpa DTO
     * @param current indeks halaman saat ini
     * @param total   total halaman
     * @return pesan terformat ringkas
     */
    private TdApi.FormattedText formatNoData(SlikSessionCache.SlikPageData data, int current, int total) {
        MessageBuilder mb = new MessageBuilder();
        mb.bold("📊 HASIL SLIK — " + extractDisplayName(data.contentKey())); mb.append("\n");
        mb.append("━━━━━━━━━━━━━━━━━━━━━\n");
        mb.append("🪪 No KTP: ");
        if (isNotBlank(data.idNumber())) mb.code(data.idNumber()); else mb.italic("Tidak ditemukan");
        mb.append("\n\n");
        mb.italic("_(Data identitas tidak tersedia)_\n");
        if (isNotBlank(data.idNumber())) {
            mb.append("\n🎯 "); mb.code("/slik " + data.idNumber()); mb.append("\n");
        }
        mb.append("📥 "); mb.code("/doc " + data.contentKey()); mb.append("\n");
        mb.italic("📄 Halaman " + (current + 1) + " dari " + total);
        return mb.build();
    }

    /**
     * Mengekstrak nama tampilan dari nama file konten S3.
     * Menghapus ekstensi file dan prefix kode AO (bagian sebelum underscore pertama).
     */
    private String extractDisplayName(String contentKey) {
        if (!isNotBlank(contentKey)) return "-";
        // Hapus ekstensi
        int dotIdx = contentKey.lastIndexOf('.');
        String name = dotIdx > 0 ? contentKey.substring(0, dotIdx) : contentKey;
        // Hapus prefix AO (sebelum underscore pertama)
        int underscoreIdx = name.indexOf('_');
        if (underscoreIdx >= 0 && underscoreIdx < name.length() - 1) {
            name = name.substring(underscoreIdx + 1);
        }
        return name;
    }

    // ─────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────

    /**
     * Mencari nilai kolektibilitas terburuk dari riwayat 24 bulan fasilitas.
     * Nilai kolektibilitas adalah angka, jadi yang terbesar adalah yang terburuk.
     *
     * @param tahunBulan map berisi data historis per bulan (key berformat "tahunBulanNNKol")
     * @return nilai kolektibilitas terburuk sebagai string, atau null jika tidak ada data
     */
    private String findWorstKol(Map<String, String> tahunBulan) {
        return tahunBulan.entrySet().stream()
            .filter(e -> e.getKey().endsWith("Kol") && isNotBlank(e.getValue()))
            .map(Map.Entry::getValue)
            .max(Comparator.comparingInt(s -> { try { return Integer.parseInt(s.trim()); } catch (Exception ex) { return 0; } }))
            .orElse(null);
    }

    /**
     * Mencari hari tunggakan terlama dari riwayat 24 bulan fasilitas.
     *
     * @param tahunBulan map berisi data historis per bulan (key berformat "tahunBulanNNHt")
     * @return hari tunggakan terbesar sebagai string, atau null jika tidak ada data
     */
    private String findMaxHt(Map<String, String> tahunBulan) {
        return tahunBulan.entrySet().stream()
            .filter(e -> e.getKey().endsWith("Ht") && isNotBlank(e.getValue()))
            .map(Map.Entry::getValue)
            .max(Comparator.comparingInt(s -> { try { return Integer.parseInt(s.trim()); } catch (Exception ex) { return 0; } }))
            .orElse(null);
    }

    /**
     * Mencari periode paling akhir (NN terbesar) dari riwayat tahunBulan dan
     * mengembalikannya sebagai label bulan-tahun (mis. "Mar 2026").
     * Key {@code tahunBulanNN} berisi nilai periode {@code yyyyMM}.
     */
    private String findKolPeriodLabel(Map<String, String> tahunBulan) {
        OptionalInt maxNN = tahunBulan.keySet().stream()
            .filter(k -> k.endsWith("Kol") && k.startsWith("tahunBulan") && isNotBlank(tahunBulan.get(k)))
            .mapToInt(k -> {
                try {
                    return Integer.parseInt(k.substring("tahunBulan".length(), k.length() - "Kol".length()));
                } catch (Exception e) { return 0; }
            })
            .filter(n -> n > 0)
            .max();
        if (maxNN.isEmpty()) return null;
        String period = tahunBulan.get(String.format("tahunBulan%02d", maxNN.getAsInt()));
        if (isNotBlank(period) && period.length() >= 6) {
            try {
                YearMonth ym = YearMonth.parse(period.substring(0, 6), DateTimeFormatter.ofPattern("yyyyMM"));
                return ym.format(DateTimeFormatter.ofPattern("MMM yyyy", new Locale("id", "ID")));
            } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * Memformat string tanggal-waktu dari format {@code yyyyMMddHHmmss}
     * menjadi format tampilan {@code dd/MM/yyyy HH:mm}.
     *
     * @param raw string tanggal-waktu mentah dari JSON
     * @return string tanggal-waktu yang sudah diformat, atau tanda "-" jika tidak valid
     */
    private String formatDateTime(String raw) {
        if (!isNotBlank(raw) || raw.length() < 14) return orDash(raw);
        try { return LocalDateTime.parse(raw, DATETIME_IN).format(DATETIME_OUT); } catch (Exception e) { return raw; }
    }

    /**
     * Memformat string tanggal dari format {@code yyyyMMdd} menjadi
     * format tampilan {@code dd/MM/yyyy}.
     *
     * @param raw string tanggal mentah dari JSON (8 karakter pertama digunakan)
     * @return string tanggal yang sudah diformat, atau tanda "-" jika tidak valid
     */
    private String formatDate(String raw) {
        if (!isNotBlank(raw) || raw.length() < 8) return orDash(raw);
        try { return LocalDate.parse(raw.substring(0, 8), DATE_IN).format(DATE_OUT); } catch (Exception e) { return raw; }
    }

    /**
     * Memformat string periode dalam format {@code yyyyMM} menjadi
     * format tampilan {@code MM/yyyy}.
     *
     * @param raw string periode 6 karakter dari JSON
     * @return string periode yang sudah diformat, atau tanda "-" jika tidak valid
     */
    private String formatYearMonth(String raw) {
        if (!isNotBlank(raw) || raw.length() < 6) return orDash(raw);
        try { return raw.substring(4, 6) + "/" + raw.substring(0, 4); } catch (Exception e) { return raw; }
    }

    /**
     * Memformat nilai numerik menjadi string rupiah dengan pemisah ribuan
     * menggunakan titik (standar Indonesia). Contoh: 1500000 → "Rp1.500.000".
     *
     * @param raw string angka yang ingin diformat
     * @return string rupiah yang sudah diformat, atau "Rp0" jika kosong/null
     */
    private String formatRupiah(String raw) {
        if (!isNotBlank(raw)) return "Rp0";
        try {
            long v = Long.parseLong(raw.trim());
            DecimalFormatSymbols sym = new DecimalFormatSymbols();
            sym.setGroupingSeparator('.');
            sym.setDecimalSeparator(',');
            return new DecimalFormat("Rp#,##0", sym).format(v);
        } catch (NumberFormatException e) { return raw; }
    }

    /**
     * Mengembalikan string asli jika tidak kosong/null, atau tanda "-"
     * sebagai pengganti jika kosong.
     */
    private String orDash(String s) { return isNotBlank(s) ? s : "-"; }

    /** Mengecek apakah string tidak null dan tidak kosong (setelah trim). */
    private boolean isNotBlank(String s) { return s != null && !s.isBlank(); }

    // ─────────────────────────────────────────────
    // MessageBuilder — builds FormattedText with entities
    // Offset dihitung dalam UTF-16 code units (sesuai TDLib).
    // Java String.length() sudah mengembalikan jumlah char UTF-16.
    // ─────────────────────────────────────────────

    /**
     * Builder sederhana untuk membangun {@link TdApi.FormattedText} yang
     * mengandung teks dengan berbagai entitas (bold, code, italic, blockquote).
     * Setiap method menambahkan teks dan langsung mendaftarkan entitasnya
     * dengan offset yang tepat dalam satuan UTF-16 code unit.
     */
    private static class MessageBuilder {
        private final StringBuilder sb = new StringBuilder();
        private final List<TdApi.TextEntity> entities = new ArrayList<>();

        MessageBuilder append(String s) { if (s != null) sb.append(s); return this; }

        MessageBuilder bold(String s) { return entity(s, new TdApi.TextEntityTypeBold()); }
        MessageBuilder code(String s) { return entity(s, new TdApi.TextEntityTypeCode()); }
        MessageBuilder italic(String s) { return entity(s, new TdApi.TextEntityTypeItalic()); }

        int markStart() { return sb.length(); }

        void endExpandableBlockquote(int start) {
            int len = sb.length() - start;
            if (len > 0) addEntity(start, len, new TdApi.TextEntityTypeExpandableBlockQuote());
        }

        int length() { return sb.length(); }

        TdApi.FormattedText build() {
            return new TdApi.FormattedText(sb.toString(), entities.toArray(new TdApi.TextEntity[0]));
        }

        private MessageBuilder entity(String s, TdApi.TextEntityType type) {
            if (s == null || s.isEmpty()) return this;
            int start = sb.length();
            sb.append(s);
            addEntity(start, s.length(), type);
            return this;
        }

        private void addEntity(int offset, int length, TdApi.TextEntityType type) {
            if (length <= 0) return;
            TdApi.TextEntity e = new TdApi.TextEntity();
            e.offset = offset;
            e.length = length;
            e.type = type;
            entities.add(e);
        }
    }
}
