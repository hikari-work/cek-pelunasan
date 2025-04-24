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
            String[] collectLabels = {"02", "03", "04", "05"};
            List<Long> customerIdAndReturnListOfCollectNumber = customerHistoryService.findCustomerIdAndReturnListOfCollectNumber(cif);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("📄 *Ringkasan Kolektibilitas untuk CustomerID: ").append(cif).append("*\n\n");
            for (int i = 0; i < collectLabels.length; i++) {
                long count = (i < customerIdAndReturnListOfCollectNumber.size() ? customerIdAndReturnListOfCollectNumber.get(i) : 0L);
                stringBuilder.append("• Status  ").append(collectLabels[i]).append(": ").append(count).append(" hari total\n");
            }
            sendMessage(chatId, stringBuilder.toString(), telegramClient);
        });
    }
}
