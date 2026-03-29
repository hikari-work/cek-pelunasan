package org.cekpelunasan.platform.telegram.command.handler;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.core.service.customerhistory.CustomerHistoryService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class InfoCifCommandHandler extends AbstractCommandHandler {

	private final CustomerHistoryService customerHistoryService;

	@Override
	public String getCommand() {
		return "/infocif";
	}

	@Override
	public String getDescription() {
		return "Informasi CIF dengan Keterlambatan";
	}

	@Override
	@RequireAuth(roles = {AccountOfficerRoles.ADMIN, AccountOfficerRoles.AO, AccountOfficerRoles.PIMP})
	public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
		return super.process(update, telegramClient);
	}

	@Override
	@Async
	public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			String cif = text.replace("/infocif ", "").trim();
			List<Long> collectCounts = customerHistoryService.findCustomerIdAndReturnListOfCollectNumber(cif);
			sendMessage(chatId, formatCollectSummary(cif, collectCounts), telegramClient);
		});
	}

	private String formatCollectSummary(String cif, List<Long> counts) {
		String[] statuses = {"🌟 LANCAR", "⚜️ DALAM PERHATIAN", "⭐ KURANG LANCAR", "💫 DIRAGUKAN", "❗ MACET"};
		long total = counts.stream().mapToLong(Long::longValue).sum();
		StringBuilder sb = new StringBuilder();
		sb.append("📊 *RINGKASAN KOLEKTIBILITAS*\n")
			.append("╔══════════════════════════\n")
			.append("║ 🆔 CIF: `").append(cif).append("`\n")
			.append("╠══════════════════════════\n")
			.append("║ 📈 *STATUS KREDIT*\n");
		for (int i = 0; i < counts.size(); i++) {
			if (counts.get(i) > 0) {
				double percent = (counts.get(i) * 100.0) / total;
				sb.append(String.format("║ %s: %d hari (%.1f%%)\n", statuses[i], counts.get(i), percent));
			}
		}
		sb.append("╚══════════════════════════\n")
			.append("⚡️ _Update: ")
			.append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM HH:mm")))
			.append("_");
		return sb.toString();
	}
}
