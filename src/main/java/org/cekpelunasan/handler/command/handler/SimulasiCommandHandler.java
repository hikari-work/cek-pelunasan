package org.cekpelunasan.handler.command.handler;

import org.cekpelunasan.entity.SimulasiResult;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.service.AuthorizedChats;
import org.cekpelunasan.service.SimulasiService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

@Component
public class SimulasiCommandHandler implements CommandProcessor {
	private final AuthorizedChats authorizedChats;
	private final SimulasiService simulasiService;

	public SimulasiCommandHandler(AuthorizedChats authorizedChats, SimulasiService simulasiService) {
		this.authorizedChats = authorizedChats;
		this.simulasiService = simulasiService;
	}

	@Override
	public String getCommand() {
		return "/simulasi";
	}

	@Override
	public String getDescription() {
		return "Melakukan simulasi pelunasan dengan format: /simulasi <No SPK> <Nominal>";
	}

	@Override
	@Async
	public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			if (!authorizedChats.isAuthorized(chatId)) {
				sendMessage(chatId, "⚠️ Maaf, kamu belum terdaftar sebagai pengguna resmi. Silakan hubungi admin untuk akses.", telegramClient);
				return;
			}

			String[] data = text.trim().split("\\s+");
			if (data.length < 3) {
				sendMessage(chatId, "❌ Format salah!\nGunakan format seperti ini:\n/simulasi <No SPK> <Nominal>\nContoh: /simulasi 123456789012 5000000", telegramClient);
				return;
			}

			String noSpk = data[1];
			String nominalStr = data[2];

			if (!noSpk.matches("\\d{12}")) {
				sendMessage(chatId, "❌ Nomor SPK harus berupa 12 digit angka.\nContoh yang benar: 123456789012", telegramClient);
				return;
			}

			long nominal;
			try {
				nominal = Long.parseLong(nominalStr);
			} catch (NumberFormatException e) {
				sendMessage(chatId, "❌ Nominal harus berupa angka. Contoh: 5000000", telegramClient);
				return;
			}

			SimulasiResult simulasi = simulasiService.getSimulasi(noSpk, nominal);
			String response = String.format(
				"""
					📊 *Hasil Simulasi Pelunasan*
					
					🧾 No SPK: `%s`
					💰 Masuk Pokok: *Rp%s*
					🏦 Masuk Bunga: *Rp%s*
					⏳ Keterlambatan hingga: *%s*""",
				noSpk,
				formatCurrency(simulasi.getMasukP()),
				formatCurrency(simulasi.getMasukI()),
				simulasi.getMaxDate()
			);
			sendMessage(chatId, response, telegramClient);
		});
	}

	private String formatCurrency(long amount) {
		return String.format("%,d", amount).replace(',', '.');
	}
}
