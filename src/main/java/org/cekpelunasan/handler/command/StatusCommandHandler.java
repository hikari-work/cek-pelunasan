package org.cekpelunasan.handler.command;

import org.cekpelunasan.entity.Repayment;
import org.cekpelunasan.service.RepaymentService;
import org.cekpelunasan.service.UserService;
import org.cekpelunasan.utils.SystemUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

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
    @Async
    public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
        if (update.getMessage() == null) {
            return CompletableFuture.completedFuture(null);
        }

        long chatId = update.getMessage().getChatId();
        long startTime = System.currentTimeMillis();

        CompletableFuture<Repayment> latestRepaymentFuture = CompletableFuture.supplyAsync(repaymentService::findAll);
        CompletableFuture<Long> totalUsersFuture = CompletableFuture.supplyAsync(userService::countUsers);
        CompletableFuture<Integer> totalRepaymentsFuture = CompletableFuture.supplyAsync(repaymentService::countAll);
        CompletableFuture<String> systemLoadFuture = CompletableFuture.supplyAsync(() -> new SystemUtils().getSystemUtils());

        return CompletableFuture.allOf(latestRepaymentFuture, totalUsersFuture, totalRepaymentsFuture, systemLoadFuture)
                .thenComposeAsync(aVoid -> {
                    try {
                        Repayment latestRepayment = latestRepaymentFuture.get();
                        Long totalUsers = totalUsersFuture.get();
                        int totalRepayments = totalRepaymentsFuture.get();
                        String systemLoad = systemLoadFuture.get();
                        long executionTime = System.currentTimeMillis() - startTime;

                        String statusMessage = buildStatusMessage(latestRepayment,
                                totalUsers,
                                totalRepayments,
                                systemLoad,
                                executionTime);
                        sendMessage(chatId, statusMessage, telegramClient);
                    } catch (Exception e) {
                        log.error("Error Send Message");
                    }
                    return CompletableFuture.completedFuture(null);
                });
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
