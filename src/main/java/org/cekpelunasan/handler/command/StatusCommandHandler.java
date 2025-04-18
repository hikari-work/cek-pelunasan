package org.cekpelunasan.handler.command;

import org.cekpelunasan.entity.Repayment;
import org.cekpelunasan.service.RepaymentService;
import org.cekpelunasan.service.UserService;
import org.cekpelunasan.utils.SystemUtils;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Component
public class StatusCommandHandler implements CommandProcessor {

    private final RepaymentService repaymentService;
    private final UserService userService;

    public StatusCommandHandler(RepaymentService repaymentService, UserService userService) {
        this.repaymentService = repaymentService;
        this.userService = userService;
    }

    @Override
    public String getCommand() {
        return "/status";
    }

    @Override
    public void process(Update update, TelegramClient telegramClient) {
        if (update.getMessage() == null) return;

        long chatId = update.getMessage().getChatId();
        long startTime = System.currentTimeMillis();

        Repayment latestRepayment = repaymentService.findAll();
        Long totalUsers = userService.countUsers();
        int totalRepayments = repaymentService.countAll();
        String systemLoad = new SystemUtils().getSystemUtils();
        long executionTime = System.currentTimeMillis() - startTime;

        String statusMessage = buildStatusMessage(latestRepayment,
                totalUsers,
                totalRepayments,
                systemLoad,
                executionTime);
        sendMessage(chatId, statusMessage, telegramClient);
    }

    private String buildStatusMessage(Repayment latest,
                                      long totalUsers,
                                      int totalRepayments,
                                      String systemLoad,
                                      long executionTime) {
        return String.format("""
                🔧 **Status Bot - Pelunasan Bot** 🔧
                
                Bot sedang **aktif** dan siap menerima perintah. Berikut adalah informasi terkini:
                
                - **Waktu Terakhir Update**: 📅 *%s*
                - **Jumlah Pengguna Terdaftar**: 📊 *%d*
                - **Total Data Pelunasan**: 📦 *%d*
                - **Load System**: ⚙️ *%s*
                
                Jika kamu ingin mencoba fitur lainnya, ketik `/help` untuk mendapatkan panduan lengkap! 🚀
                
                🔋 *Bot Dalam Keadaan Sehat*
                _Eksekusi dalam %d ms_
                """,
                latest.getCreatedAt(), totalUsers, totalRepayments, systemLoad, executionTime
        );
    }
}
