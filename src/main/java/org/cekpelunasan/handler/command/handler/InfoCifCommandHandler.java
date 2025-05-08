package org.cekpelunasan.handler.command.handler;

import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.service.CustomerHistoryService;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class InfoCifCommandHandler implements CommandProcessor {
    private final CustomerHistoryService customerHistoryService;

    public InfoCifCommandHandler(CustomerHistoryService customerHistoryService1) {
        this.customerHistoryService = customerHistoryService1;
    }

    @Override
    public String getCommand() {
        return "/infocif";
    }

    @Override
    public String getDescription() {
        return """
                Informasi CIF dengan Keterlambatan
                """;
    }

    @Override
    public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
        return CompletableFuture.runAsync(() -> {
            String cif = text.replace("/infocif ", "");
            String[] collectLabels = {"01","02", "03", "04", "05"};
            List<Long> customerIdAndReturnListOfCollectNumber = customerHistoryService.findCustomerIdAndReturnListOfCollectNumber(cif);
            String stringBuilder = String.format("""
                            📊 *RINGKASAN KOLEKTIBILITAS*
                            ╔══════════════════════════
                            ║ 🆔 CIF: `%s`
                            ╠══════════════════════════
                            ║
                            ║ 📈 *STATUS KREDIT*
                            ║ ┌────────────────────────
                            ║ │ %s
                            ║ └────────────────────────
                            ╚══════════════════════════
                            
                            ⚡️ _Data diperbarui otomatis_
                            """,
                    cif,
                    formatCollectStatus(collectLabels, customerIdAndReturnListOfCollectNumber)
            );

            sendMessage(chatId, stringBuilder, telegramClient);
        });
    }

    private String formatCollectStatus(String[] labels, List<Long> counts) {
        StringBuilder status = new StringBuilder("📊 *RINGKASAN KOLEKTIBILITAS*\n\n");

        String[] badges = {
                "🌟 LANCAR",
                "⚜️ DALAM PERHATIAN",
                "⭐ KURANG LANCAR",
                "💫 DIRAGUKAN",
                "❗ MACET"
        };

        long total = counts.stream().mapToLong(Long::valueOf).sum();

        for (int i = 0; i < labels.length; i++) {
            if (counts.get(i) > 0) {
                double percentage = (counts.get(i) * 100.0) / total;
                String bar = generateProgressBar(percentage);

                status.append(String.format("""
                %s
                %s
                %d hari (%.1f%%)
                
                """,
                        badges[i],
                        bar,
                        counts.get(i),
                        percentage
                ));
            }
        }

        status.append(String.format("""
        📌 *Total Hari:* %d
        ⏱️ _Update terakhir: %s_
        """,
                total,
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd MMM HH:mm"))
        ));

        return status.toString();
    }

    private String generateProgressBar(double percentage) {
        int blocks = (int) (percentage / 10);
        StringBuilder bar = new StringBuilder();

        for (int i = 0; i < 10; i++) {
            bar.append(i < blocks ? "█" : "▁");
        }

        return bar.toString();
    }
}