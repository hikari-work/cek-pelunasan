package org.cekpelunasan.utils;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.core.entity.Savings;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CanvasingUtils {

	private final RupiahFormatUtils rupiahFormatUtils;

	public String canvasingTab(Savings dto) {
		return String.format("""
            👤 *%s*
            📊 *Data Nasabah*
            • 🆔 CIF: `%s`
            • 📍 Alamat: %s
            • 💵 Saldo: %s
            
            """,
			dto.getName(),
			dto.getCif(),
			dto.getAddress(),
			rupiahFormatUtils.formatRupiah(dto.getBalance().longValue())
		);
	}

}
