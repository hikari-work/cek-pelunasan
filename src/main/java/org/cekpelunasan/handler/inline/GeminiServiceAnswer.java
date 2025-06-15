package org.cekpelunasan.handler.inline;

import org.cekpelunasan.service.gemini.GeminiService;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerInlineQuery;
import org.telegram.telegrambots.meta.api.objects.inlinequery.InlineQuery;
import org.telegram.telegrambots.meta.api.objects.inlinequery.inputmessagecontent.InputTextMessageContent;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResultArticle;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class GeminiServiceAnswer {

	private final GeminiService geminiService;

	public GeminiServiceAnswer(GeminiService geminiService) {
		this.geminiService = geminiService;
	}

	public void handleInlineQuery(InlineQuery inlineQuery, TelegramClient client) {
		String text = inlineQuery.getQuery();
		String id = inlineQuery.getId();

		CompletableFuture.supplyAsync(() -> geminiService.askGemini(text))
			.thenAccept(answer -> {
				InputTextMessageContent content = InputTextMessageContent.builder()
					.messageText(removeMarkdownSymbols(answer))
					.parseMode("Markdown")
					.build();
				InlineQueryResultArticle inlineQueryResultArticle = InlineQueryResultArticle.builder()
					.id(id)
					.title("Jawaban Gemini")
					.inputMessageContent(content)
					.build();
				AnswerInlineQuery answerInlineQuery = AnswerInlineQuery.builder()
					.inlineQueryId(id)
					.results(List.of(inlineQueryResultArticle))
					.cacheTime(1)
					.build();
				try {
					client.execute(answerInlineQuery);
				} catch (TelegramApiException e) {
					throw new RuntimeException(e);
				}
			});
	}
	public static String removeMarkdownSymbols(String text) {
		if (text == null) return "";
		return text
			.replace("*", "")
			.replace("_", "")
			.replace("`", "")
			.replace("[", "")
			.replace("]", "")
			.replace("\\", ""); // HAPUS backslash
	}


}
