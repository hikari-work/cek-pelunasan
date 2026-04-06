package org.cekpelunasan.platform.whatsapp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.platform.whatsapp.dto.webhook.WhatsAppWebhookDTO;
import org.cekpelunasan.platform.whatsapp.service.hotkolek.HandleKolekCommand;
import org.cekpelunasan.platform.whatsapp.service.jatuhbayar.JatuhBayarService;
import org.cekpelunasan.platform.whatsapp.service.pelunasan.HandlerPelunasan;
import org.cekpelunasan.platform.whatsapp.service.shortcut.ShortcutMessages;
import org.cekpelunasan.platform.whatsapp.service.slik.SlikService;
import org.cekpelunasan.platform.whatsapp.service.tabungan.TabunganService;
import org.cekpelunasan.platform.whatsapp.service.virtualaccount.VirtualAccountHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Jantung dari alur pemrosesan pesan WhatsApp — menentukan ke service mana setiap pesan diarahkan.
 * <p>
 * Setiap pesan yang masuk dari webhook dilempar dulu ke sini. Routers membaca isi pesan
 * dan memutuskan: apakah ini perintah hot kolek, cek pelunasan, tabungan, jatuh bayar,
 * SLIK, virtual account, atau shortcut admin? Masing-masing punya aturan pengenalan sendiri
 * berdasarkan prefix perintah (titik ".") atau slash admin ("/").
 * </p>
 * <p>
 * Semua pemrosesan dijalankan secara asinkron lewat {@link #handle(WhatsAppWebhookDTO)},
 * jadi webhook bisa langsung direspons tanpa harus menunggu proses selesai.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class Routers {

	private final HandlerPelunasan handlerPelunasan;
	private final TabunganService tabunganService;
	private final JatuhBayarService jatuhBayarService;
	private final ShortcutMessages shortcutMessages;
	private final HandleKolekCommand handleKolekCommand;
	private final SlikService slikService;
	private final VirtualAccountHandler virtualAccountHandler;

	@Value("${admin.whatsapp}")
	private String adminWhatsApp;

	private static final String COMMAND_PREFIX = ".";
	private static final String ADMIN_SHORTCUT_PREFIX = "/";
	private static final String HOT_KOLEK_PATTERN = "^" + COMMAND_PREFIX + "\\d{12}(?:\\s\\d{12})*$";

	/**
	 * Titik masuk utama untuk semua pesan WhatsApp yang diterima dari webhook.
	 * <p>
	 * Method ini memfilter pesan yang bukan teks (misalnya gambar atau stiker
	 * tanpa teks) dan mengabaikannya. Pesan teks yang valid kemudian diteruskan
	 * ke {@link #processCommand(WhatsAppWebhookDTO)} untuk diidentifikasi.
	 * </p>
	 *
	 * @param webhook data webhook lengkap dari WhatsApp gateway
	 * @return CompletableFuture yang selesai setelah routing selesai
	 */
	@Async
	@SuppressWarnings("UnusedReturnValue")
	public CompletableFuture<Void> handle(WhatsAppWebhookDTO webhook) {
		log.info("Received webhook event={} id={} from={}", webhook.getEvent(),
				webhook.getPayload() != null ? webhook.getPayload().getId() : null,
				webhook.getFrom());

		if (!isValidTextMessage(webhook)) {
			log.debug("Skipping non-text or invalid webhook event={}", webhook.getEvent());
			return CompletableFuture.completedFuture(null);
		}
		processCommand(webhook);
		return CompletableFuture.completedFuture(null);
	}

	private void processCommand(WhatsAppWebhookDTO webhook) {
		String messageText = webhook.getPayload().getBody();
		log.info("Received message: {} from Admin {}", messageText, webhook.getFrom());

		if (isAdminShortcut(webhook)) {
			log.info("Routing to Shortcut Service");
			shortcutMessages.sendShortcutMessage(webhook);
			return;
		}

		if (!messageText.startsWith(COMMAND_PREFIX)) {
			return;
		}
		log.info("Is Command");

		routeCommand(webhook, messageText);
	}

	private void routeCommand(WhatsAppWebhookDTO webhook, String text) {
		if (isHotKolekCommand(webhook)) {
			log.info("Routing to Hot Kolek Service, isGroup={}", webhook.isGroupChat());
			handleKolekCommand.handleKolekCommand(webhook);
		} else if (isPelunasanCommand(webhook)) {
			handlerPelunasan.handlePelunasan(webhook).join();
		} else if (isTabunganCommand(text)) {
			tabunganService.handleTabungan(webhook);
		} else if (isMinimalBayarCommand(webhook, text)) {
			log.warn("Minimal Bayar command not yet implemented");
		} else if (isJatuhBayarCommand(webhook, text)) {
			jatuhBayarService.handle(webhook);
		} else if (isValidSlikCommand(webhook)) {
			slikService.handleSlikService(webhook);
		} else if (isResetCommand(webhook, text)) {
			log.warn("Reset Hot Kolek command not yet implemented");
		} else if (isVirtualAccount(webhook)) {
			log.info("Routing to Virtual Account Service");
			virtualAccountHandler.handler(webhook);
		} else {
			log.debug("Unknown command: {}", text);
		}
	}

	private boolean isVirtualAccount(WhatsAppWebhookDTO webhookDTO) {
		return webhookDTO.getPayload().getBody().startsWith(COMMAND_PREFIX + "va");
	}

	private boolean isValidTextMessage(WhatsAppWebhookDTO webhook) {
		return "message".equals(webhook.getEvent())
				&& webhook.getPayload() != null
				&& webhook.getPayload().getBody() != null;
	}

	private boolean isValidSlikCommand(WhatsAppWebhookDTO webhook) {
		return isValidTextMessage(webhook)
				&& webhook.getPayload().getBody().startsWith(COMMAND_PREFIX + "s");
	}

	private boolean isAdminShortcut(WhatsAppWebhookDTO webhook) {
		return isFromAdmin(webhook)
				&& webhook.getPayload().getBody().startsWith(ADMIN_SHORTCUT_PREFIX);
	}

	private boolean isFromAdmin(WhatsAppWebhookDTO webhook) {
		return webhook.getFrom() != null && webhook.getFrom().contains(adminWhatsApp);
	}

	private boolean isAdminInGroup(WhatsAppWebhookDTO webhook) {
		return webhook.isGroupChat()
				&& webhook.getCleanSenderId() != null
				&& webhook.getCleanSenderId().equals(adminWhatsApp);
	}

	/**
	 * Mengecek apakah pesan adalah perintah hot kolek.
	 * Format yang dikenali: titik diikuti 12 digit angka, bisa beberapa SPK dipisah spasi.
	 * Contoh: ".010600001234" atau ".010600001234 010600005678".
	 *
	 * @param webhook data webhook yang akan dicek
	 * @return {@code true} kalau pesan cocok dengan pola hot kolek
	 */
	public boolean isHotKolekCommand(WhatsAppWebhookDTO webhook) {
		return isValidTextMessage(webhook)
				&& webhook.getPayload().getBody().matches(HOT_KOLEK_PATTERN);
	}

	/**
	 * Mengecek apakah pesan adalah perintah cek pelunasan.
	 * Dikenali dari prefix ".p" (titik p).
	 *
	 * @param webhook data webhook yang akan dicek
	 * @return {@code true} kalau pesan dimulai dengan ".p"
	 */
	public boolean isPelunasanCommand(WhatsAppWebhookDTO webhook) {
		return isValidTextMessage(webhook)
				&& webhook.getPayload().getBody().startsWith(COMMAND_PREFIX + "p");
	}

	private boolean isTabunganCommand(String text) {
		return text.startsWith(COMMAND_PREFIX + "t");
	}

	private boolean isMinimalBayarCommand(WhatsAppWebhookDTO webhook, String text) {
		return text.startsWith(COMMAND_PREFIX + "min") && isAdminInGroup(webhook);
	}

	private boolean isJatuhBayarCommand(WhatsAppWebhookDTO webhook, String text) {
		return text.startsWith(COMMAND_PREFIX + "jb") && isFromAdmin(webhook);
	}

	private boolean isResetCommand(WhatsAppWebhookDTO webhook, String text) {
		return text.startsWith(COMMAND_PREFIX + "reset") && isAdminInGroup(webhook);
	}
}
