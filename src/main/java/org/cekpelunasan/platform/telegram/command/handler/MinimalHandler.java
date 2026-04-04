package org.cekpelunasan.platform.telegram.command.handler;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.core.service.simulasi.SimulasiService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;



import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class MinimalHandler extends AbstractCommandHandler {

	private final SimulasiService simulasiService;

	@Override
	public String getCommand() {
		return "/minimal";
	}

	@Override
	public String getDescription() {
		return "";
	}

	@Override
	@RequireAuth(roles = {AccountOfficerRoles.ADMIN, AccountOfficerRoles.AO, AccountOfficerRoles.PIMP})
	public CompletableFuture<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
		return super.process(update, client);
	}

	@Override
	@Async
	public CompletableFuture<Void> process(long chatId, String text, SimpleTelegramClient client) {
		return CompletableFuture.runAsync(() -> {
			String noSpk = text.replace("/minimal ", "");
			if (!noSpk.matches("\\d{12}")) {
				sendMessage(chatId, "❌ Nomor SPK harus berupa 12 digit angka.\nContoh yang benar: 123456789012", client);
				return;
			}
			long minimalBayar = simulasiService.minimalBayar(noSpk).block();
			if (minimalBayar > 0) {
				sendMessage(chatId, String.format("""
					📊 *Hasil Minimal Masuk Angsuran*

					_Ini adalah fitur BETA_
					_Laporkan jika ada kesalahan perhitungan_

					🧾 No SPK: `%s`
					💰 Minimal bayar: *Rp%s*
					""",
					noSpk,
					String.format("%,d", minimalBayar).replace(',', '.')
				), client);
			} else {
				sendMessage(chatId, "Angsuran Aman Sampai Akhir Bulan", client);
			}
		});
	}
}
