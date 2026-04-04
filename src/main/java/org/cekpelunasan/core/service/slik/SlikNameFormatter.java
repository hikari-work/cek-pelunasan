package org.cekpelunasan.core.service.slik;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlikNameFormatter {

    private static final DateTimeFormatter DATETIME_IN  = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter DATE_IN      = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATETIME_OUT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter DATE_OUT     = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ObjectMapper objectMapper;

    public Mono<SlikJsonDto> parse(byte[] jsonBytes) {
        return Mono.fromCallable(() -> objectMapper.readValue(jsonBytes, SlikJsonDto.class))
            .subscribeOn(Schedulers.boundedElastic())
            .onErrorResume(e -> {
                log.error("Failed to parse SLIK JSON: {}", e.getMessage());
                return Mono.empty();
            });
    }

    public String format(SlikSessionCache.SlikPageData data, int current, int total) {
        if (data.dto() == null) {
            return formatNoData(data, current, total);
        }
        return formatFull(data, current, total);
    }

    private String formatFull(SlikSessionCache.SlikPageData data, int current, int total) {
        SlikJsonDto dto = data.dto();
        SlikJsonDto.Individual ind = dto.individual();
        SlikJsonDto.Header hdr = dto.header();

        String namaDebitur = "";
        if (ind.dataPokokDebitur() != null && !ind.dataPokokDebitur().isEmpty()) {
            namaDebitur = ind.dataPokokDebitur().get(0).namaDebitur();
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("📊 *HASIL SLIK — %s*\n", escape(namaDebitur)));
        sb.append("━━━━━━━━━━━━━━━━━━━━━\n");

        if (hdr != null) {
            sb.append(String.format("📋 Ref: `%s`\n", orDash(hdr.kodeReferensiPengguna())));
            sb.append(String.format("📅 Tanggal: %s\n", formatDateTime(hdr.tanggalPermintaan())));
        }

        if (ind.dataPokokDebitur() != null && !ind.dataPokokDebitur().isEmpty()) {
            SlikJsonDto.DataPokokDebitur deb = ind.dataPokokDebitur().get(0);
            sb.append("\n👤 *DATA DEBITUR*\n");
            sb.append("┌─────────────────────\n");
            sb.append(String.format("│ 🪪 KTP: `%s`\n", orDash(deb.noIdentitas())));
            sb.append(String.format("│ 📍 Alamat: %s\n", orDash(deb.alamat())));
            if (isNotBlank(deb.tempatLahir()) || isNotBlank(deb.tanggalLahir())) {
                sb.append(String.format("│ 🎂 Lahir: %s, %s\n",
                    orDash(deb.tempatLahir()), formatDate(deb.tanggalLahir())));
            }
            sb.append(String.format("│ 💼 Pekerjaan: %s\n", orDash(deb.pekerjaanKet())));
            sb.append("└─────────────────────\n");
        }

        if (ind.ringkasanFasilitas() != null) {
            SlikJsonDto.RingkasanFasilitas rf = ind.ringkasanFasilitas();
            sb.append("\n📈 *RINGKASAN FASILITAS*\n");
            sb.append("┌─────────────────────\n");
            sb.append(String.format("│ Kol. Terburuk: *%s*\n", orDash(rf.kualitasTerburuk())));
            sb.append(String.format("│ Bulan Data: %s\n", formatYearMonth(rf.kualitasBulanDataTerburuk())));
            sb.append(String.format("│ Plafon Total: %s\n", formatRupiah(rf.plafonEfektifTotal())));
            sb.append(String.format("│ Baki Debet Total: %s\n", formatRupiah(rf.bakiDebetTotal())));
            sb.append("└─────────────────────\n");
        }

        if (ind.fasilitas() != null && ind.fasilitas().kreditPembiayan() != null) {
            List<SlikJsonDto.KreditPembiayaan> list = ind.fasilitas().kreditPembiayan();
            sb.append(String.format("\n💳 *FASILITAS KREDIT (%d)*\n", list.size()));
            for (int i = 0; i < list.size(); i++) {
                SlikJsonDto.KreditPembiayaan k = list.get(i);
                sb.append(i == 0 ? "┌─────────────────────\n" : "├─────────────────────\n");
                sb.append(String.format("│ *%d. %s*\n", i + 1, orDash(k.ljkKet())));
                if (isNotBlank(k.cabangKet())) {
                    sb.append(String.format("│    Cabang: %s\n", k.cabangKet()));
                }
                sb.append(String.format("│    Plafon: %s\n", formatRupiah(k.plafonAwal())));
                sb.append(String.format("│    Baki Debet: %s\n", formatRupiah(k.bakiDebet())));
                sb.append(String.format("│    Kondisi: %s  |  Kol: %s\n",
                    orDash(k.kondisiKet()), orDash(k.kualitasKet())));
                if (isNotBlank(k.jenisPenggunaanKet())) {
                    sb.append(String.format("│    Penggunaan: %s\n", k.jenisPenggunaanKet()));
                }
                if (isNotBlank(k.tanggalAkadAwal()) || isNotBlank(k.tanggalJatuhTempo())) {
                    sb.append(String.format("│    Jangka: %s → %s\n",
                        formatDate(k.tanggalAkadAwal()), formatDate(k.tanggalJatuhTempo())));
                }
            }
            sb.append("└─────────────────────\n");
        }

        if (isNotBlank(data.idNumber())) {
            sb.append(String.format("\n🎯 `/slik %s`\n", data.idNumber()));
        }

        sb.append(String.format("\n📄 _Halaman %d dari %d_", current + 1, total));
        return sb.toString();
    }

    private String formatNoData(SlikSessionCache.SlikPageData data, int current, int total) {
        return String.format("""
            📄 *DOKUMEN #%d*
            ━━━━━━━━━━━━━━━━━━━━━
            📂 File: `%s`
            🪪 No KTP: %s
            ℹ️ _Data SLIK tidak tersedia_

            📄 _Halaman %d dari %d_""",
            current + 1,
            data.contentKey(),
            isNotBlank(data.idNumber()) ? "`" + data.idNumber() + "`" : "_Tidak ditemukan_",
            current + 1, total);
    }

    private String formatDateTime(String raw) {
        if (!isNotBlank(raw) || raw.length() < 14) return orDash(raw);
        try {
            return LocalDateTime.parse(raw, DATETIME_IN).format(DATETIME_OUT);
        } catch (Exception e) {
            return raw;
        }
    }

    private String formatDate(String raw) {
        if (!isNotBlank(raw) || raw.length() < 8) return orDash(raw);
        try {
            return LocalDate.parse(raw.substring(0, 8), DATE_IN).format(DATE_OUT);
        } catch (Exception e) {
            return raw;
        }
    }

    private String formatYearMonth(String raw) {
        if (!isNotBlank(raw) || raw.length() < 6) return orDash(raw);
        try {
            String year  = raw.substring(0, 4);
            String month = raw.substring(4, 6);
            return month + "/" + year;
        } catch (Exception e) {
            return raw;
        }
    }

    private String formatRupiah(String raw) {
        if (!isNotBlank(raw)) return "Rp0";
        try {
            long value = Long.parseLong(raw.trim());
            DecimalFormatSymbols sym = new DecimalFormatSymbols();
            sym.setGroupingSeparator('.');
            sym.setDecimalSeparator(',');
            return new DecimalFormat("Rp#,##0", sym).format(value);
        } catch (NumberFormatException e) {
            return raw;
        }
    }

    private String orDash(String s) {
        return isNotBlank(s) ? s : "-";
    }

    private boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String escape(String s) {
        return isNotBlank(s) ? s : "-";
    }
}
