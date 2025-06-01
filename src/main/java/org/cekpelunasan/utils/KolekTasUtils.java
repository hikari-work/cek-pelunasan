package org.cekpelunasan.utils;

import org.cekpelunasan.entity.KolekTas;
import org.springframework.stereotype.Component;

@Component
public class KolekTasUtils {

	private final FormatPhoneNumberUtils formatPhoneNumberUtils;

	public KolekTasUtils(FormatPhoneNumberUtils formatPhoneNumberUtils) {
		this.formatPhoneNumberUtils = formatPhoneNumberUtils;
	}

	public String buildKolekTas(KolekTas kolekTas) {
    return String.format("""
            ┌──────────────────────┐
            │ 👤 *%s*
            │ 📝 Rek: `%s`
            │ 📍 Alamat: %s
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
        formatPhoneNumberUtils.formatPhoneNumber(kolekTas.getNoHp()),
        kolekTas.getKolek()
    );
}

	private String formatAddress(String address) {
    	return address.length() > 30 ? address.substring(0, 29) + "..." : address;
	}
}