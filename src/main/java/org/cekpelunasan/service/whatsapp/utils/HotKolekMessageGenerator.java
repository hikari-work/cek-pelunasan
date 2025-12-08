package org.cekpelunasan.service.whatsapp.utils;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.cekpelunasan.entity.Bills;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

@Component
public class HotKolekMessageGenerator {
	public String generateMessage(List<LocationBills> locationBills) {

		String bulanTahun = getMonthAndYear(LocalDate.now());
		StringBuilder builder = new StringBuilder(String.format("""
		*HOT COLLECTION BULAN %s*
		*TAGIHAN YG PENGARUH NPL*
		
		""", bulanTahun));

		for (LocationBills location : locationBills) {
			builder.append("\n");
			if (!location.hasAnyData()) {
				continue;
			}
			builder.append("*").append(location.getName()).append("* : \n");
			for (CategoryBills category : location.getCategoryBills()) {
				builder.append("\n");
				if (category.isEmpty()) {
					continue;
				}
				if (!category.getHeader().isEmpty()) {
					builder.append(category.getHeader()).append("\n");
				}
				appendBillsInfo(builder, category.getBills());
			}
		}
		builder.append("""
		
		Bagi AO yg sudah mendapat tagihan dan tdk pengaruh NPL bisa langsung di hapus.
		Semoga NPL bulan ini bisa turun ,ttp semangat dan jaga kesehatan.""");
		return builder.toString();
	}

	private void appendBillsInfo(StringBuilder builder, List<Bills> bills) {
		if (bills == null || bills.isEmpty()) {
			return;
		}

		for (int i = 0; i < bills.size(); i++) {
			Bills bill = bills.get(i);
			if (bill == null) continue;

			String name = truncate(bill.getName());
			String line = String.format(
				"%2d. %-13s %-22s %10s",
				i + 1,
				bill.getNoSpk() != null ? bill.getNoSpk() : "",
				"*" + name + "*",
				formatToShort(bill.getDebitTray())
			);

			builder.append(line).append("\n");
		}
	}
	private String truncate(String text) {
		if (text == null) return "";
		return text.length() <= 20 ? text : text.substring(0, 20);
	}
	public static String formatToShort(long value) {
		if (value >= 1_000_000) {
			double jt = value / 1_000_000.0;
			return trimDecimal(jt) + " Jt";
		} else if (value >= 1_000) {
			double rb = value / 1_000.0;
			return trimDecimal(rb) + "rb";
		} else {
			return String.valueOf(value);
		}
	}
	private static String trimDecimal(double val) {
		if (val == (long) val) {
			return String.format("%d", (long) val);
		} else {
			return String.format("%.1f", val);
		}
	}


	@Getter
	@AllArgsConstructor
	public static class LocationBills {
		private final String name;
		private final List<CategoryBills> categoryBills;

		public boolean hasAnyData() {
			return categoryBills.stream().anyMatch(cat -> !cat.isEmpty());
		}
	}

	@Getter
	@AllArgsConstructor
	public static class CategoryBills {
		private final String header;
		private final List<Bills> bills;


		public boolean isEmpty() {
			return bills.isEmpty();
		}

	}
	private String getMonthAndYear(LocalDate date) {
		String bulan = date.getMonth().getDisplayName(TextStyle.FULL, new Locale("id", "ID"))
			.toUpperCase();
		int tahun = date.getYear();
		return bulan + " " + tahun;
	}
}
