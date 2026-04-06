package org.cekpelunasan.platform.telegram.command.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.core.service.simulasi.SimulasiService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class SimulasiCommandHandler extends AbstractCommandHandler {

	private final SimulasiService simulasiService;

	@Override
	public String getCommand() {
		return "/simulasi";
	}

	@Override
	public String getDescription() {
		return "Melakukan simulasi pelunasan dengan format: /simulasi <No SPK> <Nominal>";
	}

	@Override
	@RequireAuth(roles = {AccountOfficerRoles.ADMIN, AccountOfficerRoles.AO, AccountOfficerRoles.PIMP})
	public Mono<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
		return super.process(update, client);
	}

	@Override
	public Mono<Void> process(long chatId, String text, SimpleTelegramClient client) {
		String[] data = text.trim().split("\\s+");
		if (data.length < 3) {
			return Mono.fromRunnable(() ->
				sendMessage(chatId, "❌ Format salah!\nGunakan format seperti ini:\n/simulasi <No SPK> <Nominal>\nContoh: /simulasi 123456789012 5000000", client));
		}

		String noSpk = data[1];
		String nominalStr = data[2];

		if (!noSpk.matches("\\d{12}")) {
			return Mono.fromRunnable(() ->
				sendMessage(chatId, "❌ Nomor SPK harus berupa 12 digit angka.\nContoh yang benar: 123456789012", client));
		}

		long nominal;
		try {
			nominal = Long.parseLong(nominalStr);
		} catch (NumberFormatException e) {
			return Mono.fromRunnable(() -> sendMessage(chatId, "❌ Nominal harus berupa angka. Contoh: 5000000", client));
		}

		return simulasiService.getSimulasi(noSpk, nominal)
			.flatMap(simulasi -> Mono.fromRunnable(() -> {
				String response = String.format(
					"""
						📊 *Hasil Simulasi Masuk Angsuran*

						_Ini adalah fitur BETA_
						_Laporkan jika ada kesalahan perhitungan_

						🧾 No SPK: `%s`
						💰 Masuk Pokok: *Rp%s*
						🏦 Masuk Bunga: *Rp%s*
						⏳ Keterlambatan hingga: *%s*""",
					noSpk,
					formatCurrency(simulasi.getMasukP()),
					formatCurrency(simulasi.getMasukI()),
					simulasi.getMaxDate()
				);
				sendMessage(chatId, response, client);
			}))
			.then();
	}

	private String formatCurrency(long amount) {
		return String.format("%,d", amount).replace(',', '.');
	}
}
