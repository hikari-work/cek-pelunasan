package org.cekpelunasan.utils;

import org.cekpelunasan.entity.Repayment;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class RepaymentCalculator {

	public String calculate(Repayment repayment, Map<String, Long> penaltyMap) {
		Long bakidebet = repayment.getAmount();
		Long tunggakan = repayment.getInterest();
		Long denda = repayment.getPenaltyRepayment();
		Long penalty = penaltyMap.get("penalty");
		Long total = bakidebet + tunggakan + denda + penalty;

		return String.format("""
            🏦 *RINCIAN PELUNASAN KREDIT*

            👤 *Nasabah*
            • SPK: `%s`
            • Nama: *%s*
            • Alamat: %s
            • Produk: %s
            • Plafond: %s

            💳 *Tagihan*
            • Baki Debet: %s
            • Tunggakan: %s
            • Penalty +%s: %s
            • Denda: %s

            💵 *TOTAL TAGIHAN: %s*

            📌 *Catatan*
            • Tap SPK untuk salin nomor.
            • Hubungi Admin bila ada pertanyaan.

            ⏱️ _Generated: %s_
            """,
			formatText(repayment.getCustomerId()),
			formatText(repayment.getName()),
			formatText(repayment.getAddress()),
			formatText(repayment.getProduct()),
			formatRupiah(repayment.getPlafond()),
			formatRupiah(bakidebet),
			formatRupiah(tunggakan),
			penaltyMap.get("multiplier"),
			formatRupiah(penalty),
			formatRupiah(denda),
			formatRupiah(total),
			LocalDateTime.now().format(
				DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
			)
		);
	}


	private String formatRupiah(Long amount) {
		if (amount == null) return "Rp0";
		DecimalFormatSymbols symbols = new DecimalFormatSymbols();
		symbols.setGroupingSeparator('.');
		symbols.setDecimalSeparator(',');
		DecimalFormat df = new DecimalFormat("Rp#,##0", symbols);
		return df.format(amount);
	}

	private String formatText(String text) {
		return text == null ? "-" : text;
	}
}
