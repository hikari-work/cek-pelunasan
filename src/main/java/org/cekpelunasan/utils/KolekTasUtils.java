package org.cekpelunasan.utils;

import org.cekpelunasan.entity.KolekTas;
import org.springframework.stereotype.Component;

@Component
public class KolekTasUtils {

	public KolekTasUtils() {
	}

	public String buildKolekTas(KolekTas kolekTas) {
    return String.format("""
            ┌──────────────────────┐
            │ 👤 *%s*
            │ 📝 Rek: `%s`
            │ 📍 Alamat: %s
            └──────────────────────┘
            💰 *Rincian*
            ┌──────────────────────┐
            │ 💸 Tunggakan: %s
            │ ✨ Kelompok: %s
            │ 📱 No.HP: %s
            │ 📊 Kolek: %s
            └──────────────────────┘
            
            """,
        kolekTas.getNama(),
        kolekTas.getRekening(),
        formatAddress(kolekTas.getAlamat()),
        kolekTas.getNominal(),
        kolekTas.getKelompok(),
        formatPhoneNumber(kolekTas.getNoHp()),
        kolekTas.getKolek()
    );
}

	private String formatAddress(String address) {
    	return address.length() > 35 ? address.substring(0, 32) + "..." : address;
	}

	private String formatPhoneNumber(String phone) {
    	if (phone == null || phone.trim().isEmpty()) {
        	return "📵 Tidak tersedia";
    	}
    	String formatted = phone.startsWith("0") ? phone : "0" + phone;
    	return String.format("%s %s",
        	formatted.startsWith("08") ? "📱" : "☎️",
        	formatted.replaceAll("(\\d{4})(\\d{4})(\\d+)", "$1-$2-$3")
    	);
	}
}