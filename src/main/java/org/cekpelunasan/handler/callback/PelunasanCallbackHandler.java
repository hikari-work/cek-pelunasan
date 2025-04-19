package org.cekpelunasan.handler.callback;

import org.cekpelunasan.entity.Repayment;
import org.cekpelunasan.service.RepaymentService;
import org.cekpelunasan.utils.BackKeyboardUtils;
import org.cekpelunasan.utils.PenaltyUtils;
import org.cekpelunasan.utils.RepaymentCalculator;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class PelunasanCallbackHandler implements CallbackProcessor {

    private final RepaymentService repaymentService;

    public PelunasanCallbackHandler(RepaymentService repaymentService) {
        this.repaymentService = repaymentService;
    }

    @Override
    public String getCallBackData() {
        return "pelunasan";
    }

    @Override
    @Async
    public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
        return CompletableFuture.runAsync(() -> {
            long start = System.currentTimeMillis();

            var callback = update.getCallbackQuery();
            var message = callback.getMessage();
            var chatId = message.getChatId();
            var messageId = message.getMessageId();
            var data = callback.getData();

            try {
                Long customerId = parseCustomerId(data);
                Repayment repayment = repaymentService.findRepaymentById(customerId);

                if (repayment == null) {
                    sendMessage(chatId, "❌ Data tidak ditemukan.", telegramClient);
                    return;
                }

                Map<String, Long> penalty = new PenaltyUtils().penalty(
                        repayment.getStartDate(),
                        repayment.getPenaltyLoan(),
                        repayment.getProduct()
                );

                String result = new RepaymentCalculator().calculate(repayment, penalty);
                String response = result + "\n\n_Eksekusi dalam " + (System.currentTimeMillis() - start) + "ms_";

                editMessageWithMarkup(chatId, messageId, response, telegramClient, new BackKeyboardUtils().backButton(data));

            } catch (NumberFormatException e) {
                sendMessage(chatId, "❌ Format ID tidak valid.", telegramClient);
            } catch (Exception e) {
                sendMessage(chatId, "⚠️ Terjadi kesalahan. Silakan coba lagi.", telegramClient);
            }
        });
    }

    private Long parseCustomerId(String data) {
        // Format expected: pelunasan_<id>
        String[] parts = data.split("_");
        if (parts.length < 2) throw new NumberFormatException("Format callback salah.");
        return Long.parseLong(parts[1]);
    }
}