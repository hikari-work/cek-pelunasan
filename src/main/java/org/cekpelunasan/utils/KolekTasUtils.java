package org.cekpelunasan.utils;

import org.cekpelunasan.entity.KolekTas;
import org.springframework.stereotype.Component;

@Component
public class KolekTasUtils {

	public KolekTasUtils() {
	}

	public String buildKolekTas(KolekTas kolekTas) {
		return "👤 *" + kolekTas.getNama() + "*\n" +
			"No. Rek: `" + kolekTas.getRekening() + "`\n" +
			"Alamat: " + kolekTas.getAlamat() + "\n\n" +
			"💰 Saldo: `" + kolekTas.getNominal() + "\n\n" +
			"✨ Kelompok: `" + kolekTas.getKelompok() + "\n\n";
	}
}
