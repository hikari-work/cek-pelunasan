package org.cekpelunasan.platform.telegram.command.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.core.service.slik.SlikSessionCache;
import org.cekpelunasan.utils.button.SlikMonthPickerButton;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Handler untuk perintah {@code /slik} — langkah pertama pencarian SLIK.
 *
 * <p>Setelah menerima query (nama atau NIK), bot menampilkan keyboard pilih
 * bulan/tahun. Pencarian sebenarnya dilakukan oleh {@code SlikMonthCallbackHandler}
 * setelah user memilih bulan.</p>
 *
 * <ul>
 *   <li>{@code /slik Budi Santoso} → pilih bulan → tampilkan hasil paginasi</li>
 *   <li>{@code /slik 3201234567890001} → pilih bulan → pilih fasilitas → kirim PDF</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlikCommand extends AbstractCommandHandler {

	private static final String KTP_ID_PATTERN = "\\b\\d{16}\\b";
	private static final String COMMAND_PREFIX  = "/slik ";

	private static final String ERROR_EMPTY    = "⚠️ No KTP harus diisi\n\nGunakan: `/slik <16 digit KTP>` atau `/slik <nama>`";
	private static final String PICK_MONTH_MSG = "📅 Pilih bulan dan tahun:";

	private final SlikSessionCache slikSessionCache;
	private final SlikMonthPickerButton monthPickerButton;

	@Override
	public String getCommand() { return "/slik"; }

	@Override
	public String getDescription() { return "Cari data KTP berdasarkan NIK (16 digit) atau nama"; }

	@Override
	@RequireAuth(roles = { AccountOfficerRoles.AO, AccountOfficerRoles.ADMIN, AccountOfficerRoles.PIMP })
	public Mono<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
		return super.process(update, client);
	}

	@Override
	public Mono<Void> process(long chatId, String text, SimpleTelegramClient client) {
		String query = text.replace(COMMAND_PREFIX, "").trim();
		if (query.isEmpty()) {
			return Mono.fromRunnable(() -> telegramMessageService.sendText(chatId, ERROR_EMPTY, client));
		}

		String type = query.matches(KTP_ID_PATTERN) ? "ktp" : "name";
		log.info("/slik chatId={} type={} query={}", chatId, type, query);

		slikSessionCache.putPending(chatId, new SlikSessionCache.PendingQuery(query, type));

		return Mono.fromRunnable(() ->
			telegramMessageService.sendKeyboard(chatId, monthPickerButton.build(), client, PICK_MONTH_MSG));
	}
}
