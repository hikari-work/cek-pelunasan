package org.cekpelunasan.utils;

import org.springframework.stereotype.Component;

@Component
public class FormatPhoneNumberUtils {

	public String formatPhoneNumber(String phone) {
		if (phone == null || phone.trim().isEmpty()) {
			return "📵 Tidak tersedia";
		}
		String formatted = phone.startsWith("0") ? phone : "0" + phone;
		return String.format("%s %s",
			formatted.startsWith("08") ? "📱" : "☎️",
			formatted.replaceAll("(\\d{4})(\\d{4})(\\d+)", "$1-$2-$3"));

	}
}