package org.cekpelunasan.handler.command.handler;

import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.handler.command.template.MessageTemplate;
import org.cekpelunasan.service.AuthorizedChats;
import org.cekpelunasan.service.Bill.BillService;
import org.cekpelunasan.utils.button.ButtonListForSelectBranch;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Component
public class TagihWithNameCommandHandler implements CommandProcessor {

    private final BillService billService;
    private final AuthorizedChats authorizedChats1;
    private final MessageTemplate messageTemplate;

    public TagihWithNameCommandHandler(BillService billService, AuthorizedChats authorizedChats1, MessageTemplate messageTemplate) {
        this.billService = billService;
        this.authorizedChats1 = authorizedChats1;
        this.messageTemplate = messageTemplate;
    }

    @Override
    public String getCommand() {
        return "/tgnama";
    }

    @Override
    public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
        return CompletableFuture.runAsync(() -> {
            long chatId = update.getMessage().getChatId();
            String[] parts = update.getMessage().getText().split(" ", 2);

            if (!authorizedChats1.isAuthorized(chatId)) {
                sendMessage(chatId, messageTemplate.unathorizedMessage(), telegramClient);
                return;
            }

            if (parts.length < 2) {
                sendMessage(chatId, "❌ *Format tidak valid*\n\nContoh: /tgnama 1234567890", telegramClient);
                return;
            }

            String name = parts[1].trim();

            Set<String> branches = billService.listAllBrach();


            log.info("🔍 Nama: {} | Ditemukan {} cabang", name, branches.size());

            if (branches.isEmpty()) {
                sendMessage(chatId, "❌ *Data tidak ditemukan*", telegramClient);
                return;
            }

            if (branches.size() > 1) {
                InlineKeyboardMarkup markup = new ButtonListForSelectBranch().dynamicSelectBranch(branches, name);
                sendMessage(chatId, "⚠ *Terdapat lebih dari satu cabang dengan nama yang sama*\n\nSilakan pilih cabang yang sesuai:", telegramClient, markup);
            }
        });
    }

    public void sendMessage(Long chatId, String text, TelegramClient telegramClient) {
        sendMessage(chatId, text, telegramClient, null);
    }

    private void sendMessage(Long chatId, String text, TelegramClient telegramClient, InlineKeyboardMarkup markup) {
        try {
            telegramClient.execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text)
                    .parseMode("Markdown")
                    .replyMarkup(markup)
                    .build());
        } catch (Exception e) {
            log.error("❌ Gagal mengirim pesan ke chatId {}: {}", chatId, e.getMessage(), e);
        }
    }
}
