package org.cekpelunasan.platform.telegram.callback.pagination;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.core.entity.CreditHistory;
import org.cekpelunasan.platform.telegram.callback.AbstractCallbackHandler;
import org.cekpelunasan.core.service.credithistory.CreditHistoryService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaginationToCanvasing extends AbstractCallbackHandler {

    private final CreditHistoryService creditHistoryService;
    private final PaginationCanvassingButton paginationCanvassingButton;

    @Override
    public String getCallBackData() {
        return "canvasing";
    }

    @Override
    public CompletableFuture<Void> process(TdApi.UpdateNewCallbackQuery update, SimpleTelegramClient client) {
        return CompletableFuture.runAsync(() -> {
            String callbackData = new String(((TdApi.CallbackQueryPayloadData) update.payload).data, StandardCharsets.UTF_8);
            String[] data = callbackData.split("_");
            String address = data[1];
            long chatId = update.chatId;
            int page = Integer.parseInt(data[2]);
            List<String> addressList = Arrays.asList(address.split(" "));
            Page<CreditHistory> creditHistoriesPage = creditHistoryService.searchAddressByKeywords(addressList, page);
            log.info("Data = {}", callbackData);
            if (creditHistoriesPage.isEmpty()) {
                sendMessage(chatId, String.format("Data dengan alamat %s Tidak Ditemukan\n", address), client);
                log.info("Data Empty");
                return;
            }
            StringBuilder messageBuilder = new StringBuilder(String.format("\uD83D\uDCC4 Halaman " + (page + 1) + " dari %d\n\n", creditHistoriesPage.getTotalPages()));
            creditHistoriesPage.forEach(dto -> messageBuilder.append(String.format("""
                    👤 *%s*
                    ╔═══════════════════════
                    ║ 📊 *DATA NASABAH*
                    ║ ├─── 🆔 CIF   : `%s`
                    ║ ├─── 📍 Alamat : %s
                    ║ └─── 📱 Kontak : %s
                    ╚═══════════════════════

                    """,
                dto.getName(),
                dto.getCustomerId(),
                formatAddress(dto.getAddress()),
                formatPhone(dto.getPhone())
            )));

            TdApi.ReplyMarkupInlineKeyboard markup = paginationCanvassingButton.dynamicButtonName(creditHistoriesPage, page, address);
            editMessageWithMarkup(chatId, update.messageId, messageBuilder.toString(), client, markup);
        });
    }

    private String formatAddress(String address) {
        return address.length() > 40 ? address.substring(0, 37) + "..." : address;
    }

    private String formatPhone(String phone) {
        return phone == null || phone.isEmpty() ? "📵 Tidak tersedia" :
            phone.startsWith("0") ? "☎️ " + phone : "☎️ 0" + phone;
    }
}
