package org.cekpelunasan.utils;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.core.entity.Savings;
import org.springframework.stereotype.Component;

/**
 * Memformat data tabungan nasabah menjadi teks siap tampil untuk kegiatan canvasing.
 * <p>
 * Canvasing adalah kegiatan kunjungan lapangan ke nasabah. Class ini menyediakan
 * format ringkas yang berisi CIF, nama, alamat, dan saldo — informasi yang
 * paling dibutuhkan tim lapangan saat berkunjung ke nasabah.
 * </p>
 */
@Component
@RequiredArgsConstructor
public class CanvasingUtils {

	private final RupiahFormatUtils rupiahFormatUtils;

	/**
	 * Mengubah data tabungan nasabah menjadi teks ringkas untuk kegiatan canvasing.
	 * Format keluarannya berisi nama, CIF, alamat, dan saldo yang diformat dalam Rupiah.
	 *
	 * @param dto data tabungan nasabah yang akan diformat
	 * @return string teks yang siap ditampilkan di Telegram atau WhatsApp
	 */
	public String canvasingTab(Savings dto) {
		return String.format("""
            👤 *%s*
            📊 *Data Nasabah*
            • 🆔 CIF: `%s`
            • 📍 Alamat: %s
            • 💵 Saldo: %s
            
            """,
			dto.getName(),
			dto.getCif(),
			dto.getAddress(),
			rupiahFormatUtils.formatRupiah(dto.getBalance().longValue())
		);
	}

}
