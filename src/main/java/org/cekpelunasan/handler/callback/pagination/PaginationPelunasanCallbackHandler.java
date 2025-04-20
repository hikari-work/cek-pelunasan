package org.cekpelunasan.handler.callback.pagination;

import org.cekpelunasan.entity.Repayment;
import org.cekpelunasan.handler.callback.CallbackProcessor;
import org.cekpelunasan.service.RepaymentService;
import org.cekpelunasan.utils.button.ButtonListForName;
import org.cekpelunasan.utils.RupiahFormatUtils;
import org.springframework.data.domain.Page;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

@Component
public class PaginationPelunasanCallbackHandler implements CallbackProcessor {

    private final RepaymentService repaymentService;

    public PaginationPelunasanCallbackHandler(RepaymentService repaymentService) {
        this.repaymentService = repaymentService;
    }

    @Override
    public String getCallBackData() {
        return "page";
    }

    @Override
    @Async
    public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
        return CompletableFuture.runAsync(() -> {
            long start = System.currentTimeMillis();

            var callback = update.getCallbackQuery();
            var chatId = callback.getMessage().getChatId();
            var messageId = callback.getMessage().getMessageId();
            var data = callback.getData().split("_");

            String query = data[1];
            int page = Integer.parseInt(data[2]);

            Page<Repayment> repayments = repaymentService.findName(query, page, 5);

            if (repayments.isEmpty()) {
                sendMessage(chatId, "❌ Data tidak ditemukan.", telegramClient);
                return;
            }

            StringBuilder message = new StringBuilder("📄 Halaman ")
                    .append(page + 1)
                    .append(" dari ")
                    .append(repayments.getTotalPages())
                    .append("\n\n");

            buildRepaymentList(repayments, message);

            message.append("\n\n_Eksekusi dalam ").append(System.currentTimeMillis() - start).append("ms_");

            editMessageWithMarkup(chatId, messageId, message.toString(), telegramClient,
                    new ButtonListForName().dynamicButtonName(repayments, page, query));
        });
    }

    private void buildRepaymentList(Page<Repayment> repayments, StringBuilder builder) {
        RupiahFormatUtils rupiahFormatter = new RupiahFormatUtils();
        repayments.forEach(dto -> builder.append("📄 *Informasi Nasabah*\n")
                .append("🔢 *No SPK*      : `").append(dto.getCustomerId()).append("`\n")
                .append("👤 *Nama*        : ").append(dto.getName()).append("\n")
                .append("🏡 *Alamat*      : ").append(dto.getAddress()).append("\n")
                .append("💰 *Plafond*     : ").append(rupiahFormatter.formatRupiah(dto.getPlafond())).append("\n\n"));
    }
}
