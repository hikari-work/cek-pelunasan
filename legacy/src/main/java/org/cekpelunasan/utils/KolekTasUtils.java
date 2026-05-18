package org.cekpelunasan.utils;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.core.entity.KolekTas;
import org.springframework.stereotype.Component;

/**
 * Memformat data koleksi tas (KolekTas) menjadi teks yang siap ditampilkan di bot.
 * <p>
 * KolekTas adalah data nasabah yang masuk dalam program penagihan khusus.
 * Class ini menyusun informasi nasabah — nama, nomor rekening, alamat, nominal tunggakan,
 * kelompok, nomor HP, dan kolektibilitas — menjadi pesan yang mudah dibaca tim lapangan.
 * Nomor HP diformat otomatis ke format internasional, dan alamat dipotong maksimal 30 karakter
 * supaya pesan tidak terlalu panjang di layar HP.
 * </p>
 */
@Component
@RequiredArgsConstructor
public class KolekTasUtils {

	private final FormatPhoneNumberUtils formatPhoneNumberUtils;

	/**
	 * Memformat satu data KolekTas menjadi teks ringkas yang siap dikirim.
	 *
	 * @param kolekTas data nasabah dalam program penagihan khusus
	 * @return string teks yang sudah diformat dan siap dikirim
	 */
	public String buildKolekTas(KolekTas kolekTas) {
		return String.format("""
            
            👤 *%s*
            📝 Rek: `%s`
            📍 Alamat: %s
            💸 Tunggakan: %s
            ✨ Kelompok: %s
            📱 No. HP: %s
            📊 Kolek: %s
            """,
			kolekTas.getNama(),
			kolekTas.getRekening(),
			formatAddress(kolekTas.getAlamat()),
			kolekTas.getNominal(),
			kolekTas.getKelompok(),
			formatPhoneNumberUtils.formatPhoneNumber(kolekTas.getNoHp()),
			kolekTas.getKolek()
		);
	}


	private String formatAddress(String address) {
    	return address.length() > 30 ? address.substring(0, 29) + "..." : address;
	}
}