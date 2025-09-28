package org.cekpelunasan.service.whatsapp.utils;

import org.cekpelunasan.entity.Bills;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

@Service
public class GenerateMessageText {

	public String generateMessageText(List<Bills> billsKaligondang,
                                  List<Bills> firstPayKaligondang,
                                  List<Bills> dueDateKaligondang,
                                  List<Bills> billsKalikajar,
                                  List<Bills> firstPayKalikajar,
                                  List<Bills> dueDateKalikajar,
                                  List<Bills> billsKejobong,
                                  List<Bills> firstPayKejobong,
                                  List<Bills> dueDateKejobong) {
    String bulanTahun = getMonthAndYear(LocalDate.now());
    StringBuilder builder = new StringBuilder(String.format("""
        *HOT COLLECTION BULAN %s*
        *TAGIHAN YG PENGARUH NPL*
        *KALIGONDANG*
        
        """, bulanTahun));

    String[] locations = {"\n*Kaligondang*", "\n*Kalikajar*", "\n*Kejobong*"};
    List<List<Bills>>[] billsByLocation = new List[]{
        List.of(billsKaligondang, firstPayKaligondang, dueDateKaligondang),
        List.of(billsKalikajar, firstPayKalikajar, dueDateKalikajar),
        List.of(billsKejobong, firstPayKejobong, dueDateKejobong)
    };
    
    String[] categories = {"", "\n*Angsuran Pertama*", "\n*Jatuh tempo*"};

    for (int loc = 0; loc < locations.length; loc++) {
        builder.append(locations[loc]).append(" : \n");

        for (int cat = 0; cat < categories.length; cat++) {
            if (cat > 0) {
                builder.append(categories[cat]).append(" : \n");
            }
            List<Bills> bills = billsByLocation[loc].get(cat);
            appendBillsInfo(builder, bills);
        }

    }
    
    builder.append("""
		
		
		Bagi AO yg sudah mendapat tagihan dan tdk pengaruh NPL bisa langsung di hapus.
		Semoga NPL bulan ini bisa turun ,ttp semangat dan jaga kesehatan.""");
    return builder.toString();
}

	private void appendBillsInfo(StringBuilder builder, List<Bills> bills) {
		for (int i = 0; i < bills.size(); i++) {
			Bills bill = bills.get(i);

			String name = truncate(bill.getName());

			String line = String.format(
				"%2d. %-13s %-22s %10s",
				i + 1,
				bill.getNoSpk(),
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



	private String getMonthAndYear(LocalDate date) {
		String bulan = date.getMonth().getDisplayName(TextStyle.FULL, new Locale("id", "ID"))
			.toUpperCase();
		int tahun = date.getYear();
		return bulan + " " + tahun;
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
}