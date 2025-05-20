package org.cekpelunasan.utils;

import org.cekpelunasan.entity.Savings;
import org.springframework.stereotype.Component;

@Component
public class CanvasingUtils {

	private final RupiahFormatUtils rupiahFormatUtils;

	public CanvasingUtils(RupiahFormatUtils rupiahFormatUtils) {
		this.rupiahFormatUtils = rupiahFormatUtils;
	}

	public String canvasingTab(Savings dto) {
		return String.format("""
						👤 *%s*
						╔═══════════════════════
						║ 📊 *DATA NASABAH*
						║ ├─── 🆔 CIF   : `%s`
						║ ├─── 📍 Alamat: %s
						║ └─── 💵 Saldo : %s
						╚═══════════════════════
						""",
			dto.getName(),
			dto.getCif(),
			dto.getAddress(),
			rupiahFormatUtils.formatRupiah(dto.getBalance().longValue())
		);
	}
}
