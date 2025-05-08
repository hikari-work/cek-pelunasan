package org.cekpelunasan.handler.callback.pagination;

import org.cekpelunasan.entity.Repayment;
import org.cekpelunasan.handler.callback.CallbackProcessor;
import org.cekpelunasan.service.RepaymentService;
import org.cekpelunasan.utils.RupiahFormatUtils;
import org.cekpelunasan.utils.button.ButtonListForName;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

@Component
public class PaginationPelunasanCallbackHandler implements CallbackProcessor {

    private final RepaymentService repaymentService;
    private final ButtonListForName buttonListForName;

    public PaginationPelunasanCallbackHandler(RepaymentService repaymentService, ButtonListForName buttonListForName) {
        this.repaymentService = repaymentService;
        this.buttonListForName = buttonListForName;
    }

    @Override
    public String getCallBackData() {
        return "page";
    }

    @Override
    public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
        return CompletableFuture.runAsync(() -> {
            long start = System.currentTimeMillis();

            var callback = update.getCallbackQuery();
            long chatId = callback.getMessage().getChatId();
            int messageId = callback.getMessage().getMessageId();
            String[] data = callback.getData().split("_");

            String query = data[1];
            int page = Integer.parseInt(data[2]);

            Page<Repayment> repayments = repaymentService.findName(query, page, 5);
            if (repayments.isEmpty()) {
                sendMessage(chatId, "❌ Data tidak ditemukan.", telegramClient);
                return;
            }

            String message = buildRepaymentMessage(repayments, page, start);
            var keyboard = buttonListForName.dynamicButtonName(repayments, page, query);

            editMessageWithMarkup(chatId, messageId, message, telegramClient, keyboard);
        });
    }

    private String buildRepaymentMessage(Page<Repayment> repayments, int page, long startTime) {
        StringBuilder builder = new StringBuilder(String.format("""
        🏦 *SISTEM INFORMASI KREDIT*
        ═══════════════════════════
        📊 Halaman %d dari %d
        ───────────────────────────
        
        """, page + 1, repayments.getTotalPages()));

        RupiahFormatUtils formatter = new RupiahFormatUtils();
        repayments.forEach(dto -> builder.append(String.format("""
        🔷 *%s*
        ┌────────────────────────
        │ 📎 *DATA NASABAH*
        │ └── 🔖 SPK    : `%s`
        │ └── 📍 Alamat : %s
        │
        │ 💳 *INFORMASI KREDIT*
        │ └── 💰 Plafond : %s
        │ └── 📅 Status  : %s
        └────────────────────────
        
        """,
                dto.getName(),
                dto.getCustomerId(),
                dto.getAddress(),
                formatter.formatRupiah(dto.getPlafond()),
                getStatusKredit(dto.getPlafond())
        )));

        builder.append("""
        ℹ️ *Informasi*
        ▔▔▔▔▔▔▔▔▔▔▔
        📌 _Tap SPK untuk menyalin_
        ⚡️ _Proses: %dms_
        """.formatted(System.currentTimeMillis() - startTime));

        return builder.toString();
    }

    private String getStatusKredit(long plafond) {
        if (plafond > 500_000_000) return "🔴 Premium";
        if (plafond > 100_000_000) return "🟡 Gold";
        return "🟢 Regular";
    }
}