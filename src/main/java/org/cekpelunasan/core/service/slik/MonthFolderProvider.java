package org.cekpelunasan.core.service.slik;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Menyediakan prefix folder R2 berdasarkan bulan saat ini (zona WIB).
 * Format: {@code {TAHUN}_{BULAN:02d}} — contoh: {@code 2026_05}.
 *
 * <p>Digunakan oleh semua komponen yang perlu tahu di folder mana
 * file SLIK bulan ini disimpan, tanpa perlu hardcode atau konfigurasi ulang
 * setiap awal bulan.</p>
 */
@Component
public class MonthFolderProvider {

    /**
     * Mengembalikan nama folder bulan saat ini dalam format {@code YYYY_MM}.
     * Waktu dihitung dalam zona WIB (UTC+7).
     *
     * @return string folder, contoh {@code "2026_05"}
     */
    public String currentFolder() {
        LocalDate now = LocalDate.now(ZoneOffset.ofHours(7));
        return String.format("%04d_%02d", now.getYear(), now.getMonthValue());
    }

    /** Contoh: {@code "2026_05/pdf/SMG_budi.pdf"} */
    public String pdfPath(String filename) {
        return currentFolder() + "/pdf/" + filename;
    }

    /** Contoh: {@code "2026_05/txt/KTP_3175040206810003.txt"} */
    public String txtPath(String filename) {
        return currentFolder() + "/txt/" + filename;
    }

    /** Contoh: {@code "2026_05/ideb/data.ideb"} */
    public String idebPath(String filename) {
        return currentFolder() + "/ideb/" + filename;
    }
}
