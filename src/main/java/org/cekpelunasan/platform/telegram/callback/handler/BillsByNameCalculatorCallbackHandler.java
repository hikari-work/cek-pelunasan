package org.cekpelunasan.platform.telegram.callback.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.core.entity.Bills;
import org.cekpelunasan.platform.telegram.callback.AbstractCallbackHandler;
import org.cekpelunasan.platform.telegram.callback.pagination.PaginationBillsByNameCallbackHandler;
import org.cekpelunasan.core.service.bill.BillService;
import org.cekpelunasan.utils.DateUtils;
import org.cekpelunasan.utils.TagihanUtils;
import org.springframework.data.domain.Page;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class BillsByNameCalculatorCallbackHandler extends AbstractCallbackHandler {

    private static final String CALLBACK_DATA = "pagebills";
    private static final String CALLBACK_DELIMITER = "_";
    private static final int QUERY_MIN_LENGTH = 3;
    private static final int QUERY_MAX_LENGTH = 4;
    private static final int PAGE_SIZE = 5;
    private static final String ERROR_MESSAGE = "❌ *Data tidak ditemukan*";
    private static final String HEADER_MESSAGE = "📅 *Tagihan Jatuh Bayar Hari Ini*\n\n";

    private final BillService billService;
    private final DateUtils dateUtils;
    private final PaginationBillsByNameCallbackHandler paginationBillsByNameCallbackHandler;
    private final TagihanUtils tagihanUtils;

    @Override
    public String getCallBackData() {
        return CALLBACK_DATA;
    }

    @Override
    @Async
    public CompletableFuture<Void> process(TdApi.UpdateNewCallbackQuery update, SimpleTelegramClient client) {
        return CompletableFuture.runAsync(() -> {
            try {
                String callbackData = new String(((TdApi.CallbackQueryPayloadData) update.payload).data, StandardCharsets.UTF_8);
                long chatId = update.chatId;
                long messageId = update.messageId;

                CallbackData parsedData = parseCallbackData(callbackData);

                if (isValidQuery(parsedData.query())) {
                    processBillsQuery(parsedData, chatId, messageId, client);
                } else {
                    sendErrorMessage(chatId, client);
                }
            } catch (Exception e) {
                log.error("Error processing callback", e);
            }
        });
    }

    private CallbackData parseCallbackData(String data) {
        String[] parts = data.split(CALLBACK_DELIMITER);
        return CallbackData.builder()
            .query(parts.length > 1 ? parts[1] : "")
            .page(parts.length > 2 ? Integer.parseInt(parts[2]) : 0)
            .build();
    }

    private boolean isValidQuery(String query) {
        int length = query.length();
        return length == QUERY_MIN_LENGTH || length == QUERY_MAX_LENGTH;
    }

    private void processBillsQuery(CallbackData callbackData, long chatId, long messageId, SimpleTelegramClient client) {
        log.info("Finding Bills By: {}", callbackData.query());

        Page<Bills> billsPage = fetchBillsPage(callbackData);
        String messageText = buildBillsMessage(billsPage);
        TdApi.ReplyMarkupInlineKeyboard markup = buildPaginationMarkup(billsPage, callbackData);

        editMessageWithMarkup(chatId, messageId, messageText, client, markup);
    }

    private Page<Bills> fetchBillsPage(CallbackData callbackData) {
        String query = callbackData.query();
        LocalDateTime today = LocalDateTime.now();
        String convertedDate = dateUtils.converterDate(today);

        if (query.length() == QUERY_MIN_LENGTH) {
            return billService.findDueDateByAccountOfficer(query, convertedDate, callbackData.page(), PAGE_SIZE);
        } else {
            return billService.findBranchAndPayDown(query, convertedDate, callbackData.page(), PAGE_SIZE);
        }
    }

    private String buildBillsMessage(Page<Bills> billsPage) {
        StringBuilder sb = new StringBuilder(HEADER_MESSAGE);
        billsPage.forEach(bills -> sb.append(tagihanUtils.billsCompact(bills)));
        return sb.toString();
    }

    private TdApi.ReplyMarkupInlineKeyboard buildPaginationMarkup(Page<Bills> billsPage, CallbackData callbackData) {
        return paginationBillsByNameCallbackHandler.dynamicButtonName(
            billsPage,
            callbackData.page(),
            callbackData.query()
        );
    }

    private void sendErrorMessage(long chatId, SimpleTelegramClient client) {
        sendMessage(chatId, ERROR_MESSAGE, client);
    }

    private record CallbackData(String query, Integer page) {

        public static CallbackDataBuilder builder() {
            return new CallbackDataBuilder();
        }

        public static class CallbackDataBuilder {
            private String query;
            private Integer page;

            public CallbackDataBuilder query(String query) {
                this.query = query;
                return this;
            }

            public CallbackDataBuilder page(Integer page) {
                this.page = page;
                return this;
            }

            public CallbackData build() {
                return new CallbackData(query, page);
            }
        }
    }
}
