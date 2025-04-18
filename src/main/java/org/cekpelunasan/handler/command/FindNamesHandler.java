package org.cekpelunasan.handler.command;

import org.cekpelunasan.entity.Repayment;
import org.cekpelunasan.service.AuthorizedChats;
import org.cekpelunasan.service.RepaymentService;
import org.cekpelunasan.utils.ButtonListForName;
import org.cekpelunasan.utils.RupiahFormatUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Component
public class FindNamesHandler implements CommandProcessor {

    private final RepaymentService repaymentService;
    private final AuthorizedChats authService;
    private final MessageTemplate messageTemplateService;

    private static final int PAGE_SIZE = 5;
    private static final int FIRST_PAGE = 0;

    public FindNamesHandler(
            RepaymentService repaymentService,
            AuthorizedChats authService,
            MessageTemplate messageTemplateService
    ) {
        this.repaymentService = repaymentService;
        this.authService = authService;
        this.messageTemplateService = messageTemplateService;
    }

    @Override
    public String getCommand() {
        return "/fi";
    }

    @Override
    public void process(Update update, TelegramClient telegramClient) {
        long startTime = System.currentTimeMillis();
        String text = update.getMessage().getText();
        Long chatId = update.getMessage().getChatId();
        String keyword = extractKeyword(text);

        if (!authService.isAuthorized(chatId)) {
            sendMessage(chatId, messageTemplateService.unathorizedMessage(), telegramClient);
            return;
        }

        if (keyword.isEmpty()) {
            sendUsageInstruction(chatId, telegramClient);
            return;
        }

        Page<Repayment> repayments = repaymentService.findName(keyword, FIRST_PAGE, PAGE_SIZE);

        if (repayments.isEmpty()) {
            sendNoDataFoundMessage(chatId, keyword, telegramClient);
            return;
        }

        StringBuilder messageBuilder = buildRepaymentMessage(repayments);
        String footer = String.format("\n\nEksekusi dalam %dms", System.currentTimeMillis() - startTime);

        sendMessageWithReplyMarkup(chatId,
                messageBuilder.append(footer).toString(),
                telegramClient,
                new ButtonListForName().dynamicButtonName(repayments, FIRST_PAGE, keyword)
        );
    }

    private String extractKeyword(String text) {
        return text.length() > 4 ? text.substring(4).trim() : "";
    }

    private void sendUsageInstruction(Long chatId, TelegramClient telegramClient) {
        sendMessage(chatId, messageTemplateService.fiCommandHelper(), telegramClient);
    }

    private void sendNoDataFoundMessage(Long chatId, String keyword, TelegramClient telegramClient) {
        String message = String.format("""
                ❌ *Informasi* ❌
                
                Data `%s` tidak ditemukan. Periksa ejaan atau gunakan kata kunci yang lebih singkat.
                """, keyword);
        sendMessage(chatId, message, telegramClient);
    }

    private StringBuilder buildRepaymentMessage(Page<Repayment> repayments) {
        StringBuilder builder = new StringBuilder(String.format("\uD83D\uDCC4 Halaman 1 dari %d\n\n", repayments.getTotalPages()));

        repayments.forEach(dto -> builder.append("📄 *Informasi Nasabah*\n")
                .append("🔢 *No SPK*      : `").append(dto.getCustomerId()).append("`\n")
                .append("👤 *Nama*        : ").append(dto.getName()).append("\n")
                .append("🏡 *Alamat*      : ").append(dto.getAddress()).append("\n")
                .append("💰 *Plafond*     : ").append(new RupiahFormatUtils().formatRupiah(dto.getPlafond())).append("\n\n")
        );

        return builder;
    }
}
