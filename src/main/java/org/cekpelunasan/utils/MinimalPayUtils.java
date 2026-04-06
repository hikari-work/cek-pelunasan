package org.cekpelunasan.utils;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.core.entity.Bills;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MinimalPayUtils {

	private final RupiahFormatUtils rupiahFormatUtils;

	public String minimalPay(Bills bill) {
		return String.format("""
            🔑 *SPK*: `%s`
            👤 *Nama*: *%s*
            🏠 *Alamat*: %s

            💳 *Minimal Pembayaran*
            • Pokok: %s
            • Bunga: %s

            💰 *TOTAL*: %s
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
