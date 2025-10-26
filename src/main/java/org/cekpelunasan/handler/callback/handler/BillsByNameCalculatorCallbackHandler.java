package org.cekpelunasan.handler.callback.handler;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.entity.Bills;
import org.cekpelunasan.handler.callback.CallbackProcessor;
import org.cekpelunasan.handler.callback.pagination.PaginationBillsByNameCallbackHandler;
import org.cekpelunasan.service.Bill.BillService;
import org.cekpelunasan.utils.DateUtils;
import org.cekpelunasan.utils.TagihanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class BillsByNameCalculatorCallbackHandler implements CallbackProcessor {

	private static final Logger log = LoggerFactory.getLogger(BillsByNameCalculatorCallbackHandler.class);
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
	public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			try {
				var callback = update.getCallbackQuery();
				var chatId = callback.getMessage().getChatId();
				var messageId = callback.getMessage().getMessageId();

				CallbackData callbackData = parseCallbackData(callback.getData());

				if (isValidQuery(callbackData.query())) {
					processBillsQuery(callbackData, chatId, messageId, telegramClient);
				} else {
					sendErrorMessage(chatId, telegramClient);
				}
			} catch (Exception e) {
				log.error("Error processing callback", e);
			}
		});
	}

	// ===== Callback Data Parsing =====

	/**
	 * Parse callback data into structured object
	 */
	private CallbackData parseCallbackData(String data) {
		String[] parts = data.split(CALLBACK_DELIMITER);
		return CallbackData.builder()
			.query(parts.length > 1 ? parts[1] : "")
			.page(parts.length > 2 ? Integer.parseInt(parts[2]) : 0)
			.build();
	}

	/**
	 * Validate query length
	 */
	private boolean isValidQuery(String query) {
		int length = query.length();
		return length == QUERY_MIN_LENGTH || length == QUERY_MAX_LENGTH;
	}

	// ===== Bills Processing =====

	/**
	 * Process bills query and send result to Telegram
	 */
	private void processBillsQuery(CallbackData callbackData, Long chatId, Integer messageId, TelegramClient telegramClient) {
		log.info("Finding Bills By: {}", callbackData.query());

		Page<Bills> billsPage = fetchBillsPage(callbackData);
		String messageText = buildBillsMessage(billsPage);
		InlineKeyboardMarkup markup = buildPaginationMarkup(billsPage, callbackData);

		editMessage(chatId, messageId, messageText, markup, telegramClient);
	}

	/**
	 * Fetch bills page based on query type
	 */
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

	/**
	 * Build message text from bills page
	 */
	private String buildBillsMessage(Page<Bills> billsPage) {
		StringBuilder sb = new StringBuilder(HEADER_MESSAGE);
		billsPage.forEach(bills -> sb.append(tagihanUtils.billsCompact(bills)));
		return sb.toString();
	}

	/**
	 * Build pagination markup for bills
	 */
	private InlineKeyboardMarkup buildPaginationMarkup(Page<Bills> billsPage, CallbackData callbackData) {
		return paginationBillsByNameCallbackHandler.dynamicButtonName(
			billsPage,
			callbackData.page(),
			callbackData.query()
		);
	}

	// ===== Telegram Message Operations =====

	/**
	 * Edit message with new content and keyboard markup
	 */
	private void editMessage(Long chatId, Integer messageId, String text, InlineKeyboardMarkup markup, TelegramClient telegramClient) {
		try {
			log.info("Updating Bills message...");

			EditMessageText editMessage = EditMessageText.builder()
				.chatId(chatId.toString())
				.messageId(messageId)
				.text(text)
				.replyMarkup(markup)
				.parseMode("Markdown")
				.build();

			telegramClient.execute(editMessage);
		} catch (Exception e) {
			log.error("Error editing message for chatId: {}", chatId, e);
		}
	}

	/**
	 * Send error message to user
	 */
	private void sendErrorMessage(Long chatId, TelegramClient telegramClient) {
		sendMessage(chatId, ERROR_MESSAGE, telegramClient, null);
	}

	/**
	 * Send message to Telegram chat
	 */
	public void sendMessage(Long chatId, String text, TelegramClient telegramClient, InlineKeyboardMarkup markup) {
		try {
			SendMessage sendMessage = SendMessage.builder()
				.chatId(chatId.toString())
				.text(text)
				.replyMarkup(markup)
				.parseMode("Markdown")
				.build();

			telegramClient.execute(sendMessage);
			log.debug("Message sent to chatId: {}", chatId);
		} catch (Exception e) {
			log.error("Error sending message to chatId: {}", chatId, e);
		}
	}

	// ===== Helper Classes =====

	/**
	 * DTO for parsed callback data
	 */
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