package org.cekpelunasan.handler.command.handler;

import org.cekpelunasan.entity.Bills;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.handler.command.template.MessageTemplate;
import org.cekpelunasan.service.AuthorizedChats;
import org.cekpelunasan.service.Bill.BillService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

@Component
public class TagihCommandHandler implements CommandProcessor {

	private final BillService billService;
	private final AuthorizedChats authorizedChats1;
	private final MessageTemplate messageTemplate;

	public TagihCommandHandler(BillService billService, AuthorizedChats authorizedChats1, MessageTemplate messageTemplate) {
		this.billService = billService;
		this.authorizedChats1 = authorizedChats1;
		this.messageTemplate = messageTemplate;
	}

	@Override
	public String getCommand() {
		return "/tagih";
	}

	@Override
	public String getDescription() {
		return """
						Mengembalikan rincian tagihan berdasarkan
						ID SPK yang anda kirimkan
						""";
	}

	@Override
	@Async
	public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			String[] parts = text.split(" ", 2);

			if (!authorizedChats1.isAuthorized(chatId)) {
				sendMessage(chatId, messageTemplate.unathorizedMessage(), telegramClient);
				return;
			}
			if (parts.length < 2) {
				sendMessage(chatId, messageTemplate.notValidDeauthFormat(), telegramClient);
				return;
			}
			long start = System.currentTimeMillis();

			try {
				String customerNumber = parts[1];
				Bills bills = billService.getBillById(customerNumber);
				if (bills == null) {
					sendMessage(chatId, "❌ *Data tidak ditemukan*", telegramClient);
					return;
				}
				sendMessage(chatId, buildBillMessage(bills) + "\nEksekusi dalam " + (System.currentTimeMillis() - start) + " ms", telegramClient);

			} catch (Exception e) {
				log.error("Error", e);
			}
		});
	}

	public String buildBillMessage(Bills bill) {
		return String.format("""
										🏦 *INFORMASI KREDIT*
										═══════════════════
										
										👤 *Detail Nasabah*
										▢ Nama\t\t: *%s*
										▢ No SPK\t: `%s`
										▢ Alamat\t: %s
										
										💳 *Informasi Pinjaman*
										▢ Plafond\t\t: %s
										▢ Baki Debet\t: %s
										▢ Realisasi\t\t: %s
										▢ Jatuh Tempo\t: %s
										
										💹 *Angsuran*
										▢ Bunga\t\t: %s
										▢ Pokok\t\t: %s
										▢ Total\t\t: %s
										
										⚠️ *Tunggakan*
										▢ Bunga\t\t: %s
										▢ Pokok\t\t: %s
										
										📊 *Status Kredit*
										▢ Hari Tunggakan\t: %s hari
										▢ Kolektibilitas\t\t: %s
										
										💰 *Pembayaran*
										▢ Total Tagihan\t\t: %s
										
										⚡️ *Minimal Bayar*
										▢ Pokok\t\t: %s
										▢ Bunga\t\t: %s
										
										👨‍💼 *Account Officer*: %s
										═══════════════════
										""",
						bill.getName(),
						bill.getNoSpk(),
						bill.getAddress(),
						formatRupiah(bill.getPlafond()),
						formatRupiah(bill.getDebitTray()),
						bill.getRealization(),
						bill.getDueDate(),
						formatRupiah(bill.getInterest()),
						formatRupiah(bill.getPrincipal()),
						formatRupiah(bill.getInstallment()),
						formatRupiah(bill.getLastInterest()),
						formatRupiah(bill.getLastPrincipal()),
						bill.getDayLate(),
						bill.getCollectStatus(),
						formatRupiah(bill.getFullPayment()),
						formatRupiah(bill.getMinPrincipal()),
						formatRupiah(bill.getMinInterest()),
						bill.getAccountOfficer()
		);
	}

	private String formatRupiah(Long amount) {
		return String.format("Rp %,d", amount);
	}
}