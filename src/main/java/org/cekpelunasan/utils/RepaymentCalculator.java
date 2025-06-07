package org.cekpelunasan.utils;

import org.cekpelunasan.entity.Repayment;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Map;

public class RepaymentCalculator {

	public String calculate(Repayment repayment, Map<String, Long> penaltyMap) {
		Long bakidebet = repayment.getAmount();
		Long tunggakan = repayment.getInterest();
		Long denda = repayment.getPenaltyRepayment();
		Long total = bakidebet + tunggakan + denda + penaltyMap.get("penalty");

		return String.format("""
				🏦 *RINCIAN PELUNASAN KREDIT*
				┏━━━━━━━━━━━━━━━━━━━━━━━
				┃ 📊 Status: %s
				┗━━━━━━━━━━━━━━━━━━━━━━━
				
				👤 *DATA NASABAH*
				┌────────────────────────
				│ 🎫 SPK     : `%s`
				│ 👨‍💼 Nama    : *%s*
				│ 📍 Alamat  : %s
				│ 💼 Produk  : %s
				│ 💰 Plafond : %s
				└────────────────────────
				
				💳 *RINCIAN TAGIHAN*
				┌────────────────────────
				│ 📈 Baki Debet : %s
				│ ⚠️ Tunggakan   : `%s`
				│ ⏰ Penalty +%s : %s
				│ 🚫 Denda      : %s
				│
				│ 📊 *TOTAL TAGIHAN*
				│ 💵 %s
				└────────────────────────
				
				ℹ️ *CATATAN PENTING*
				┌────────────────────────
				│ • _Harap segera melunasi_
				│ • _Hindari denda tambahan_
				│ • _Tap SPK untuk menyalin_
				└────────────────────────
				""",
			getStatusBadge(total),
			formatText(repayment.getCustomerId()),
			formatText(repayment.getName()),
			formatText(repayment.getAddress()),
			formatText(repayment.getProduct()),
			formatRupiah(repayment.getPlafond()),
			formatRupiah(bakidebet),
			formatRupiah(tunggakan),
			penaltyMap.get("multiplier"),
			formatRupiah(penaltyMap.get("penalty")),
			formatRupiah(denda),
			formatRupiah(total)
		);
	}

	private String getStatusBadge(Long total) {
		if (total > 500_000_000) return "🔴 URGENT";
		if (total > 100_000_000) return "🟡 PRIORITY";
		return "🟢 NORMAL";
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
