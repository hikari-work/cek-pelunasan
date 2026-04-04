package org.cekpelunasan.utils;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.core.entity.Savings;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SavingsUtils {

	private final RupiahFormatUtils rupiahFormatUtils;


	public String getSavings(Savings saving) {
		StringBuilder message = new StringBuilder();
		long bookBalance = saving.getBalance().add(saving.getTransaction()).longValue();
		long minBalance = saving.getMinimumBalance().longValue();
		long blockBalance = saving.getBlockingBalance().longValue();
		long effectiveBalance = bookBalance - minBalance - blockBalance;

		message.append("👤 *").append(saving.getName()).append("*\n")
			.append("No. Rek: `").append(saving.getTabId()).append("`\n")
			.append("Alamat: ").append(saving.getAddress()).append("\n\n")
			.append("💰 Saldo:\n")
			.append("• Buku: ").append(rupiahFormatUtils.formatRupiah(bookBalance)).append("\n")
			.append("• Min: ").append(rupiahFormatUtils.formatRupiah(minBalance)).append("\n")
			.append("• Block: ").append(rupiahFormatUtils.formatRupiah(blockBalance)).append("\n")
			.append("• Efektif: `").append(rupiahFormatUtils.formatRupiah(effectiveBalance)).append("`\n\n");
		return message.toString();
	}
	public String buildMessage(Page<Savings> savings, int page, long startTime) {
		StringBuilder message = new StringBuilder("📊 *INFORMASI TABUNGAN*\n")
			.append("Halaman ").append(page + 1).append(" dari ").append(savings.getTotalPages()).append("\n\n");
		savings.forEach(saving -> message.append(getSavings(saving)));
		message.append("⏱️ Waktu: ").append(System.currentTimeMillis() - startTime).append("ms");
		return message.toString();
	}
}
