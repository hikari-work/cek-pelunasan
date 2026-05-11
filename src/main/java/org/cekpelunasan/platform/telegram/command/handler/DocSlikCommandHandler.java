package org.cekpelunasan.platform.telegram.command.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.core.service.slik.SlikSessionCache;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.utils.button.SlikMonthPickerButton;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Handler untuk perintah {@code /doc} — langkah pertama pengambilan dokumen SLIK.
 *
 * <p>Setelah menerima nama file, bot menampilkan keyboard pilih bulan/tahun.
 * Pengambilan file dari S3 dilakukan oleh {@code SlikMonthCallbackHandler}
 * setelah user memilih bulan. Folder akan dibentuk dari pilihan bulan
 * (mis. {@code 2026_05}) sehingga user tidak perlu mengetik path penuh.</p>
 */
@Component
@RequiredArgsConstructor
public class DocSlikCommandHandler extends AbstractCommandHandler {

	private static final String ERROR_EMPTY    = "⚠️ Nama file harus diisi\n\nGunakan: `/doc <nama_file>`";
	private static final String ERROR_PATH     = "⚠️ Cukup nama file saja, tanpa path/folder (mis. `/doc DNI_Andi.pdf`)";
	private static final String PICK_MONTH_MSG = "📅 Pilih bulan dan tahun:";

	private final SlikSessionCache slikSessionCache;
	private final SlikMonthPickerButton monthPickerButton;

	@Override
	public String getCommand() {
		return "/doc";
	}

	@Override
	public String getDescription() {
		return "";
	}

	@Override
	@RequireAuth(roles = {AccountOfficerRoles.ADMIN, AccountOfficerRoles.AO, AccountOfficerRoles.PIMP})
	public Mono<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
		return super.process(update, client);
	}

	@Override
	public Mono<Void> process(long chatId, String text, SimpleTelegramClient client) {
		String name = text.replace("/doc ", "").trim();
		if (name.isEmpty() || name.equals("/doc")) {
			return Mono.fromRunnable(() -> telegramMessageService.sendText(chatId, ERROR_EMPTY, client));
		}
		if (name.contains("/") || name.contains("\\")) {
			return Mono.fromRunnable(() -> telegramMessageService.sendText(chatId, ERROR_PATH, client));
		}

		slikSessionCache.putPending(chatId, new SlikSessionCache.PendingQuery(name, "doc"));

		return Mono.fromRunnable(() ->
			telegramMessageService.sendKeyboard(chatId, monthPickerButton.build(), client, PICK_MONTH_MSG));
	}
}
