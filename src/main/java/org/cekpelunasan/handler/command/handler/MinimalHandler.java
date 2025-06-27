package org.cekpelunasan.handler.command.handler;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.service.auth.AuthorizedChats;
import org.cekpelunasan.service.simulasi.SimulasiService;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class MinimalHandler implements CommandProcessor {

	private final AuthorizedChats authorizedChats;
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
	public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			if (!authorizedChats.isAuthorized(chatId)) {
				sendMessage(chatId, "⚠️ Maaf, kamu belum terdaftar sebagai pengguna resmi. Silakan hubungi admin untuk akses.", telegramClient);
				return;
			}
			String replace = text.replace("/minimal ", "");

			if (!replace.matches("\\d{12}")) {
				sendMessage(chatId, "❌ Nomor SPK harus berupa 12 digit angka.\nContoh yang benar: 123456789012", telegramClient);
				return;
			}
			long minimalBayar = simulasiService.minimalBayar(replace);
			if (minimalBayar > 0) {
				String response = String.format(
					"""
						📊 *Hasil Minimal Masuk Angsuran*
						
						_Ini adalah fitur BETA_
						_Laporkan jika ada kesalahan perhitungan_
						
						🧾 No SPK: `%s`
						💰 Minimal bayar: *Rp%s*
						""",
					replace,
					formatCurrency(minimalBayar)
				);
				sendMessage(chatId, response, telegramClient);
			} else {
				sendMessage(chatId, "Angsuran Aman Sampai Akhir Bulan", telegramClient);
			}



		});
	}
	private String formatCurrency(long amount) {
		return String.format("%,d", amount).replace(',', '.');
	}

}
