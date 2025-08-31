package org.cekpelunasan.handler.command.handler;

import org.cekpelunasan.dto.PelunasanDTO;
import org.cekpelunasan.entity.Bills;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.service.Bill.BillService;
import org.cekpelunasan.service.repayment.PelunasanService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

@Component
public class PelunasanHandler implements CommandProcessor {
	private final BillService billService;
	private final PelunasanService pelunasanService;

	public PelunasanHandler(BillService billService, PelunasanService pelunasanService) {
		this.billService = billService;
		this.pelunasanService = pelunasanService;
	}

	@Override
	public String getCommand() {
		return "/p";
	}

	@Override
	public String getDescription() {
		return "";
	}

	@Override
	@Async
	public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
		String spk = text.replace("/p ", "");
		if (spk.isBlank()) {
			sendMessage(chatId, "Silahkan Isi No SPK", telegramClient);
			return CompletableFuture.completedFuture(null);
		}
		log.info("Get Pelunasan for Spk {}", spk);
		Bills billsSpk = billService.getBillById(spk);
		log.info("Get Pelunasan for Spk {} {}", spk, billsSpk.toString());
		PelunasanDTO pelunasan = pelunasanService.getPelunasanById(billsSpk);
		SendMessage message = SendMessage.builder().chatId(chatId).text(generatePelunasanMessage(pelunasan)).build();
		try {
			telegramClient.execute(message);
		} catch (TelegramApiException e) {
			throw new RuntimeException(e);
		}
		return CommandProcessor.super.process(chatId, text, telegramClient);
	}
	public String generatePelunasanMessage(PelunasanDTO pelunasanDTO) {
		if (pelunasanDTO == null) {
			return "Data pelunasan tidak tersedia.";
		}


		return "=== INFORMASI PELUNASAN ===\n\n" +
			"📋 DATA NASABAH\n" +
			"SPK               : " + (pelunasanDTO.getSpk() != null ? pelunasanDTO.getSpk() : "-") + "\n" +
			"Nama           : " + (pelunasanDTO.getName() != null ? pelunasanDTO.getName() : "-") + "\n" +
			"Alamat         : " + (pelunasanDTO.getAddress() != null ? pelunasanDTO.getAddress() : "-") + "\n" +
			"Produk         : " + (pelunasanDTO.getProduct() != null ? pelunasanDTO.getProduct() : "-") + "\n" +
			"\n💰 DATA KREDIT\n" +
			"Plafond        : Rp " + formatCurrency(pelunasanDTO.getPlafond()) + "\n" +
			"Pokok Hutang   : Rp " + formatCurrency(pelunasanDTO.getAmount()) + "\n" +
			"\n📊 PERHITUNGAN BUNGA\n" +
			"Multiplier     : " + String.format("%.2f", pelunasanDTO.getMultiplier()) + " Kali Bunga\n" +
			"\nRincian:\n" +
			"• Pokok Hutang : Rp " + formatCurrency(pelunasanDTO.getAmount()) + "\n" +
			"• Bunga        : Rp " + formatCurrency(pelunasanDTO.getInterest()) + "\n" +
			"• Penalty      : Rp " + formatCurrency(pelunasanDTO.getPenalty()) + "\n" +
			"• Denda        : Rp " + formatCurrency(pelunasanDTO.getDenda()) + "\n" +
			"  " + "-".repeat(25) + "\n" +
			"  TOTAL        : Rp " + formatCurrency(pelunasanDTO.getTotal()) + "\n";
	}

	private String formatCurrency(Long amount) {
		if (amount == null) {
			return "0";
		}
		return String.format("%,d", amount);
	}
}
