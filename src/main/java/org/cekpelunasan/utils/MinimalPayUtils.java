package org.cekpelunasan.utils;

import org.cekpelunasan.entity.Bills;
import org.springframework.stereotype.Component;

@Component
public class MinimalPayUtils {

	private final RupiahFormatUtils rupiahFormatUtils;

	public MinimalPayUtils(RupiahFormatUtils rupiahFormatUtils) {
		this.rupiahFormatUtils = rupiahFormatUtils;
	}

	public String minimalPay(Bills bill) {
		return String.format("""
							┌──────────────────┐
							│ 🔐 SPK: `%s`
							│ 👤 Nama: *%s*
							│ 🏘️ Alamat: %s
							└──────────────────┘
							💳 *Rincian Pembayaran*
							┌──────────────────┐
							│ 💎 Pokok    : %s
							│ 💫 Bunga    : %s
							│
							│ 🔥 *TOTAL MINIMAL*
							│ 💰 %s
							└──────────────────┘
							""",
			bill.getNoSpk(),
			bill.getName(),
			bill.getAddress(),
			rupiahFormatUtils.formatRupiah(bill.getMinPrincipal()),
			rupiahFormatUtils.formatRupiah(bill.getMinInterest()),
			rupiahFormatUtils.formatRupiah(bill.getMinPrincipal() + bill.getMinInterest())
		);
	}
}
