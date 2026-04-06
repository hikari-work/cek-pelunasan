package org.cekpelunasan.platform.whatsapp.service.tabungan;

import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.platform.whatsapp.dto.webhook.WhatsAppWebhookDTO;
import org.cekpelunasan.core.entity.Savings;
import org.cekpelunasan.core.service.savings.SavingsService;
import org.cekpelunasan.platform.whatsapp.service.sender.WhatsAppSenderService;
import org.cekpelunasan.utils.SavingsUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Menangani perintah cek informasi tabungan nasabah lewat WhatsApp.
 * <p>
 * Mendukung dua mode pencarian yang dideteksi otomatis dari input pengguna:
 * <ul>
 *   <li><strong>Nomor rekening</strong> (12 digit angka) — langsung menampilkan detail tabungan
 *       satu nasabah tertentu</li>
 *   <li><strong>Nama</strong> — mencari nasabah yang namanya mengandung kata kunci tersebut,
 *       dengan hasil dibatasi maksimal 5 nasabah</li>
 * </ul>
 * Format perintah: {@code .t [nomor rekening]} atau {@code .t [nama nasabah]}.
 * Seperti fitur lain, admin mendapat respons lewat edit pesan, pengguna biasa mendapat pesan baru.
 * </p>
 */
@Slf4j
@Component
public class TabunganService {

	private static final int MAX_NAME_RESULTS = 5;
	private static final String ACCOUNT_PATTERN = "\\d{12}";

	private final SavingsUtils savingsUtils;
	private final WhatsAppSenderService whatsAppSenderService;
	private final SavingsService savingsService;

	@Value("${admin.whatsapp}")
	private String adminWhatsApp;

	public TabunganService(SavingsService savingsService, SavingsUtils savingsUtils, WhatsAppSenderService whatsAppSenderService) {
		this.savingsService = savingsService;
		this.savingsUtils = savingsUtils;
		this.whatsAppSenderService = whatsAppSenderService;
	}

	/**
	 * Memproses perintah cek tabungan secara asinkron.
	 * Mendeteksi mode pencarian (nomor vs nama) dan mendelegasikan ke handler yang sesuai.
	 *
	 * @param command data webhook dari perintah tabungan yang masuk
	 * @return CompletableFuture yang selesai setelah hasil dikirim ke pengguna
	 */
	@Async
	@SuppressWarnings("UnusedReturnValue")
	public CompletableFuture<Void> handleTabungan(WhatsAppWebhookDTO command) {
		if (!isValidTabunganCommand(command)) {
			return CompletableFuture.completedFuture(null);
		}

		String input = command.getPayload().getBody().substring(".t ".length()).trim();

		Mono<Void> pipeline = input.matches(ACCOUNT_PATTERN)
			? handleByAccountNumber(command, input)
			: handleByName(command, input);

		return pipeline.toFuture();
	}

	private Mono<Void> handleByAccountNumber(WhatsAppWebhookDTO command, String tabId) {
		return savingsService.findById(tabId)
			.doOnNext(saving -> {
				String message = savingsUtils.getSavings(saving);
				whatsAppSenderService.sendReactionToMessage(command.buildChatId(), command.getPayload().getId()).subscribe();
				if (command.getFrom().contains(adminWhatsApp)) {
					whatsAppSenderService.updateMessage(command.buildChatId(), command.getPayload().getId(), message).subscribe();
				} else {
					whatsAppSenderService.sendWhatsAppText(command.buildChatId(), message).subscribe();
				}
			})
			.switchIfEmpty(Mono.fromRunnable(() ->
				whatsAppSenderService.sendWhatsAppText(command.buildChatId(), "Data tidak ditemukan.").subscribe()
			))
			.then();
	}

	private Mono<Void> handleByName(WhatsAppWebhookDTO command, String name) {
		log.info("Tabungan name search — query: {}", name);
		return savingsService.findByName(name, MAX_NAME_RESULTS)
			.collectList()
			.flatMap(results -> {
				String message = buildNameSearchMessage(name, results);
				whatsAppSenderService.sendWhatsAppText(command.buildChatId(), message).subscribe();
				return Mono.empty();
			});
	}

	private String buildNameSearchMessage(String query, List<Savings> results) {
		if (results.isEmpty()) {
			return "❌ Tidak ditemukan nasabah dengan nama *" + query + "*";
		}

		StringBuilder sb = new StringBuilder();
		sb.append("🔍 *Hasil pencarian: \"").append(query).append("\"*\n");
		sb.append("Ditemukan: ").append(results.size()).append(" nasabah\n");
		sb.append("━━━━━━━━━━━━━━━━━\n\n");

		results.forEach(saving -> sb.append(savingsUtils.getSavings(saving)));

		if (results.size() == MAX_NAME_RESULTS) {
			sb.append("_Hasil dibatasi ").append(MAX_NAME_RESULTS).append(" nasabah._\n");
		}
		sb.append("\n💡 Gunakan `.t {nomor rekening}` untuk detail lengkap.");
		return sb.toString();
	}

	/**
	 * Memvalidasi apakah pesan adalah perintah cek tabungan yang valid.
	 * Perintah dianggap valid kalau dimulai dengan ".t " dan ada input setelahnya.
	 *
	 * @param command data webhook yang akan divalidasi
	 * @return {@code true} kalau format perintah valid
	 */
	public boolean isValidTabunganCommand(WhatsAppWebhookDTO command) {
		String body = command.getPayload().getBody();
		return body.startsWith(".t ") && body.length() > 3;
	}
}
