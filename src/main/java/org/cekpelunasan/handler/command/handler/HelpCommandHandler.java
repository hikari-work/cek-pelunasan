package org.cekpelunasan.handler.command.handler;

import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.handler.command.template.GenerateHelpMessage;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

@Component
public class HelpCommandHandler implements CommandProcessor {
	private final GenerateHelpMessage generateHelpMessage;

	public HelpCommandHandler(@Lazy GenerateHelpMessage generateHelpMessage) {
		this.generateHelpMessage = generateHelpMessage;
	}

	@Override
	public String getCommand() {
		return "/help";
	}

	@Override
	public String getDescription() {
		return """
						Menampilkan pesan Ini
						""";
	}

	@Override
	@Async
	public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			if (isHelpCommand(text)) {
				sendHelpMessage(chatId, telegramClient);
			}
		});
	}

	private boolean isHelpCommand(String text) {
		return text != null && text.trim().startsWith(getCommand());
	}

	private void sendHelpMessage(Long chatId, TelegramClient telegramClient) {
		sendMessage(chatId, """
						                🤖 *PANDUAN PERINTAH BOT*
						═══════════════════════
						
						📊 *INFORMASI & STATUS*
						┌──────────────────────
						│ 📡 */status*
						│ • Cek status bot dan database
						│
						│ 📋 */infocif* <id>
						│ • Informasi lengkap nasabah
						│
						│ ℹ️ */fi* <nama/alamat>
						│ • Cari info detail nasabah
						└──────────────────────
						
						💰 *LAYANAN KEUANGAN*
						┌──────────────────────
						│ 💳 */tagih* <spk>
						│ • Cek detail tagihan nasabah
						│
						│ 📅 */jb*
						│ • Lihat tagihan jatuh tempo
						│
						│ 💸 */pl* <spk>
						│ • Informasi pelunasan kredit
						│
						│ 🏦 */tab* <rek>
						│ • Cek saldo tabungan
						│
						│ 📑 */pabpr* <nama/alamat>
						│ • Cari data kredit PA-BPR
						└──────────────────────
						
						🔍 *PENCARIAN & CANVASING*
						┌──────────────────────
						│ 🎯 */canvasing* <alamat>
						│ • Cari nasabah di lokasi
						└──────────────────────
						
						👔 *PERINTAH ADMIN*
						┌──────────────────────
						│ ✨ */auth* <chat_id>
						│ • Memberi akses pengguna
						│
						│ ❌ */deauth* <chat_id>
						│ • Mencabut akses pengguna
						│
						│ 📢 */broadcast* <pesan>
						│ • Kirim pengumuman global
						└──────────────────────
						
						📦 *MANAJEMEN DATA*
						┌──────────────────────
						│ 📤 */uploadtagihan* <csv>
						│ • Update data tagihan
						│
						│ 📥 */uploadcredit* <csv>
						│ • Update history kredit
						│
						│ 💾 */uploadtab* <csv>
						│ • Update data tabungan
						│
						│ ✅ */validupload* <csv>
						│ • Validasi data nasabah
						└──────────────────────
						
						ℹ️ *CATATAN PENTING*
						┌──────────────────────
						│ • Gunakan format yang benar
						│ • Tunggu respon setiap upload
						│ • Data real-time & akurat
						│ • Backup sebelum update
						│ • Validasi setelah upload
						└──────────────────────
						
						⚡️ _Response time: 1-3 detik_
						🔒 _Secure & Reliable System_""", telegramClient);
	}
}
