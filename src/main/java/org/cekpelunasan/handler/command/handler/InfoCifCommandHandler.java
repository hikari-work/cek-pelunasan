package org.cekpelunasan.handler.command.handler;

import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.service.CustomerHistoryService;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class InfoCifCommandHandler implements CommandProcessor {
	private final CustomerHistoryService customerHistoryService;

	public InfoCifCommandHandler(CustomerHistoryService customerHistoryService) {
		this.customerHistoryService = customerHistoryService;
	}

	@Override
	public String getCommand() {
		return "/infocif";
	}

	@Override
	public String getDescription() {
		return "Informasi CIF dengan Keterlambatan";
	}

	@Override
	public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			String cif = text.replace("/infocif ", "");
			List<Long> collectCounts = customerHistoryService.findCustomerIdAndReturnListOfCollectNumber(cif);
			
			String message = formatCollectSummary(cif, collectCounts);
			sendMessage(chatId, message, telegramClient);
		});
	}

	private String formatCollectSummary(String cif, List<Long> counts) {
		StringBuilder sb = new StringBuilder();
		String[] statuses = {
			"🌟 LANCAR", 
			"⚜️ DALAM PERHATIAN", 
			"⭐ KURANG LANCAR", 
			"💫 DIRAGUKAN", 
			"❗ MACET"
		};
		
		long total = counts.stream().mapToLong(Long::valueOf).sum();
		
		sb.append("📊 *RINGKASAN KOLEKTIBILITAS*\n")
		  .append("╔══════════════════════════\n")
		  .append("║ 🆔 CIF: `").append(cif).append("`\n")
		  .append("╠══════════════════════════\n")
		  .append("║\n")
		  .append("║ 📈 *STATUS KREDIT*\n")
		  .append("║ ┌────────────────────────\n");
		
		for (int i = 0; i < counts.size(); i++) {
			if (counts.get(i) > 0) {
				double percent = (counts.get(i) * 100.0) / total;
				sb.append(String.format("║ │ %s: %d hari (%.1f%%)\n", 
				                        statuses[i], counts.get(i), percent));
			}
		}
		
		sb.append("║ └────────────────────────\n")
		  .append("╚══════════════════════════\n\n")
		  .append("⚡️ _Data diperbarui: ")
		  .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM HH:mm")))
		  .append("_");
		
		return sb.toString();
	}
}