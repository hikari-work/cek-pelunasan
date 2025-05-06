package org.cekpelunasan.handler.command.handler;

import org.cekpelunasan.entity.CreditHistory;
import org.cekpelunasan.handler.callback.pagination.PaginationCanvassingButton;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.handler.command.template.MessageTemplate;
import org.cekpelunasan.service.AuthorizedChats;
import org.cekpelunasan.service.CreditHistoryService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class CanvasingCommandHandler implements CommandProcessor {
    private final AuthorizedChats authorizedChats1;
    private final MessageTemplate messageTemplate;
    private final CreditHistoryService creditHistoryService;
    private final PaginationCanvassingButton paginationCanvassingButton;

    public CanvasingCommandHandler(AuthorizedChats authorizedChats1, MessageTemplate messageTemplate, CreditHistoryService creditHistoryService, PaginationCanvassingButton paginationCanvassingButton) {
        this.authorizedChats1 = authorizedChats1;
        this.messageTemplate = messageTemplate;
        this.creditHistoryService = creditHistoryService;
        this.paginationCanvassingButton = paginationCanvassingButton;
    }

    @Override
    public String getCommand() {
        return "/canvasing";
    }

    @Override
    public String getDescription() {
        return """
                Mengembalikan List Nasabah yang pernah Kredit Namun tidak ambil lagi
                """;
    }

    @Override
    public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
        return CompletableFuture.runAsync(() -> {
            String address= text.length() > 11 ? text.substring(11).trim() : "";
            if (!authorizedChats1.isAuthorized(chatId)) {
                sendMessage(chatId, messageTemplate.unathorizedMessage(), telegramClient);
                return;
            }
            if (address.isEmpty()) {
                sendMessage(chatId, "Alamat Harus Diisi", telegramClient);
                return;
            }
            List<String> addressList = Arrays.stream(text.split(" ")).filter(s -> !s.equals("/canvasing")).toList();
            Page<CreditHistory> creditHistories = creditHistoryService.searchAddressByKeywords(addressList, 0);

            if (creditHistories.isEmpty()) {
                sendMessage(chatId, String.format("""
                        Data dengan alamat %s Tidak Ditemukan
                        """, address), telegramClient);
                return;
            }
            StringBuilder messageBuilder = new StringBuilder(String.format("\uD83D\uDCC4 Halaman 1 dari %d\n\n", creditHistories.getTotalPages()));
            creditHistories.forEach(dto -> messageBuilder.append("📄 *Informasi Nasabah*\n")
                    .append("🔢 *No CIF*      : `").append(dto.getCustomerId()).append("`\n")
                    .append("👤 *Nama*        : ").append(dto.getName()).append("\n")
                    .append("🏡 *Alamat*      : ").append(dto.getAddress()).append("\n")
                    .append("💰 *No HP*     : ").append(dto.getPhone()).append("\n\n"));
            InlineKeyboardMarkup markup = paginationCanvassingButton.dynamicButtonName(creditHistories, 0, address);
            sendMessage(chatId, messageBuilder.toString(), telegramClient, markup);
        });


    }
    public void sendMessage(Long chatId, String text, TelegramClient telegramClient, InlineKeyboardMarkup markup) {
        try {
            telegramClient.execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text)
                    .replyMarkup(markup)
                    .parseMode("Markdown")
                    .build());
        } catch (Exception e) {
            log.error("Error");
        }
    }

}
