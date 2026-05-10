package org.cekpelunasan.utils;

import it.tdlight.jni.TdApi;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class MinBungaCalendarBuilder {

    private static final ZoneId WIB = ZoneId.of("Asia/Jakarta");
    private static final DateTimeFormatter MONTH_HEADER = DateTimeFormatter.ofPattern("MMMM yyyy", new Locale("id", "ID"));
    private static final String[] DAY_NAMES = {"Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min"};

    public TdApi.ReplyMarkupInlineKeyboard buildCalendar(String identifier, List<String> selectedDates, boolean hasSelection) {
        LocalDate today = LocalDate.now(WIB);
        YearMonth ym = YearMonth.from(today);

        List<TdApi.InlineKeyboardButton[]> rows = new ArrayList<>();

        // Baris header: nama bulan
        rows.add(new TdApi.InlineKeyboardButton[]{
            button(ym.format(MONTH_HEADER), "noop")
        });

        // Baris nama hari
        TdApi.InlineKeyboardButton[] dayNameRow = new TdApi.InlineKeyboardButton[7];
        for (int i = 0; i < 7; i++) {
            dayNameRow[i] = button(DAY_NAMES[i], "noop");
        }
        rows.add(dayNameRow);

        // Isi tanggal: mulai dari hari pertama bulan
        // Hari pertama bulan, offset ke kolom yang tepat (Senin = 0)
        LocalDate firstDay = ym.atDay(1);
        int startDayOfWeek = firstDay.getDayOfWeek().getValue() - 1; // 0=Senin, 6=Minggu

        List<TdApi.InlineKeyboardButton> currentRow = new ArrayList<>();

        // Padding sebelum hari pertama
        for (int i = 0; i < startDayOfWeek; i++) {
            currentRow.add(button(" ", "noop"));
        }

        for (int day = 1; day <= ym.lengthOfMonth(); day++) {
            LocalDate date = ym.atDay(day);
            String dateStr = date.toString(); // YYYY-MM-DD
            boolean isSelected = selectedDates.contains(dateStr);
            String label = isSelected ? "✅" + day : String.valueOf(day);
            String callbackData = "minbungacal_" + identifier + "_" + dateStr;
            currentRow.add(button(label, callbackData));

            // Jika sudah 7 kolom atau akhir bulan, tambah baris baru
            if (currentRow.size() == 7) {
                rows.add(currentRow.toArray(new TdApi.InlineKeyboardButton[0]));
                currentRow = new ArrayList<>();
            }
        }

        // Flush sisa baris jika ada
        if (!currentRow.isEmpty()) {
            while (currentRow.size() < 7) {
                currentRow.add(button(" ", "noop"));
            }
            rows.add(currentRow.toArray(new TdApi.InlineKeyboardButton[0]));
        }

        // Tombol aksi
        if (hasSelection) {
            rows.add(new TdApi.InlineKeyboardButton[]{
                button("✅ Konfirmasi", "minbungaconfirm_" + identifier),
                button("🗑 Hapus Pilihan", "minbungaclear_" + identifier)
            });
        }

        return new TdApi.ReplyMarkupInlineKeyboard(rows.toArray(new TdApi.InlineKeyboardButton[0][]));
    }

    private TdApi.InlineKeyboardButton button(String text, String callbackData) {
        TdApi.InlineKeyboardButton btn = new TdApi.InlineKeyboardButton();
        btn.text = text;
        TdApi.InlineKeyboardButtonTypeCallback type = new TdApi.InlineKeyboardButtonTypeCallback();
        type.data = callbackData.getBytes(StandardCharsets.UTF_8);
        btn.type = type;
        return btn;
    }
}
