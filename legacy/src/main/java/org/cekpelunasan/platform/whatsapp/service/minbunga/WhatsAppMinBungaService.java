package org.cekpelunasan.platform.whatsapp.service.minbunga;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.core.entity.Bills;
import org.cekpelunasan.core.service.bill.BillService;
import org.cekpelunasan.core.service.minbunga.BillsForDate;
import org.cekpelunasan.core.service.minbunga.MinBungaBillCalculatorService;
import org.cekpelunasan.platform.whatsapp.dto.webhook.WhatsAppWebhookDTO;
import org.cekpelunasan.platform.whatsapp.service.sender.WhatsAppSenderService;
import org.cekpelunasan.utils.MinBungaMessageFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class WhatsAppMinBungaService {

    private static final String COMMAND = ".minbunga ";
    private static final ZoneId WIB = ZoneId.of("Asia/Jakarta");

    private final BillService billService;
    private final MinBungaBillCalculatorService calculator;
    private final MinBungaMessageFormatter formatter;
    private final WhatsAppSenderService whatsAppSenderService;

    @Value("${admin.whatsapp}")
    private String adminWhatsApp;

    @Async
    @SuppressWarnings("UnusedReturnValue")
    public CompletableFuture<Void> handle(WhatsAppWebhookDTO webhook) {
        String chatId = webhook.buildChatId();
        String body = webhook.getPayload().getBody();

        if (!webhook.getFrom().contains(adminWhatsApp)) {
            return CompletableFuture.completedFuture(null);
        }

        if (body.length() <= COMMAND.length()) {
            send(chatId, "Format: .minbunga <cabang> <tanggal>\nContoh: .minbunga 1075 12,13,14");
            return CompletableFuture.completedFuture(null);
        }

        String args = body.substring(COMMAND.length()).trim();
        String[] parts = args.split("\\s+", 2);
        if (parts.length < 2) {
            send(chatId, "Format: .minbunga <cabang> <tanggal>\nContoh: .minbunga 1075 12,13,14");
            return CompletableFuture.completedFuture(null);
        }

        String branch = parts[0];
        String dateArg = parts[1];

        List<LocalDate> targetDates;
        try {
            targetDates = parseDates(dateArg);
        } catch (IllegalArgumentException | DateTimeException e) {
            send(chatId, "Format tanggal tidak valid. Contoh: 12,13,14 atau 12-15");
            return CompletableFuture.completedFuture(null);
        }

        log.info("WhatsApp minbunga: branch={} dates={}", branch, targetDates);

        int minDayLate = calculator.minDayLateThreshold(targetDates);
        List<Bills> allBills = billService.findMinimalBungaByBranch(branch, minDayLate).block();
        if (allBills == null || allBills.isEmpty()) {
            send(chatId, "Tidak ada data tagihan untuk cabang " + branch + ".");
            return CompletableFuture.completedFuture(null);
        }

        List<BillsForDate> grouped = calculator.calculate(allBills, targetDates);
        List<String> messages = formatter.format(grouped, branch);

        for (String msg : messages) {
            whatsAppSenderService.sendWhatsAppText(chatId, msg).subscribe();
        }

        log.info("WhatsApp minbunga: {} messages sent for branch={} to={}", messages.size(), branch, chatId);
        return CompletableFuture.completedFuture(null);
    }

    private List<LocalDate> parseDates(String dateArg) {
        YearMonth ym = YearMonth.now(WIB);
        List<LocalDate> dates = new ArrayList<>();

        if (dateArg.contains(",")) {
            for (String part : dateArg.split(",")) {
                int day = Integer.parseInt(part.trim());
                dates.add(ym.atDay(day));
            }
        } else if (dateArg.contains("-")) {
            String[] range = dateArg.split("-", 2);
            int from = Integer.parseInt(range[0].trim());
            int to = Integer.parseInt(range[1].trim());
            if (from > to) throw new IllegalArgumentException("Range tanggal tidak valid: from > to");
            for (int d = from; d <= to; d++) {
                dates.add(ym.atDay(d));
            }
        } else {
            int day = Integer.parseInt(dateArg.trim());
            dates.add(ym.atDay(day));
        }

        if (dates.isEmpty()) throw new IllegalArgumentException("Tidak ada tanggal yang diparse");
        return dates;
    }

    private void send(String chatId, String message) {
        whatsAppSenderService.sendWhatsAppText(chatId, message).subscribe();
    }
}
