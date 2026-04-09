package org.cekpelunasan.platform.whatsapp.service.virtualaccount;

import org.cekpelunasan.platform.whatsapp.dto.webhook.WhatsAppWebhookDTO;
import org.cekpelunasan.core.entity.Bills;
import org.cekpelunasan.core.entity.Savings;
import org.cekpelunasan.core.service.bill.BillService;
import org.cekpelunasan.core.service.savings.SavingsService;
import org.cekpelunasan.platform.whatsapp.service.sender.WhatsAppSenderService;
import org.springframework.stereotype.Component;

/**
 * Menangani perintah cek nomor Virtual Account (VA) untuk pembayaran angsuran.
 * <p>
 * Ketika pengguna mengetik ".va [nomor SPK atau nomor rekening]", handler ini mencari
 * data akun (bisa kredit atau tabungan), lalu menghasilkan nomor VA untuk empat bank:
 * Mandiri, BRI, Danamon, dan BNI. Nomor VA setiap bank dibuat dengan format yang berbeda
 * berdasarkan prefix bank dan nomor akun yang ada.
 * </p>
 * <p>
 * Kalau yang ditemukan adalah rekening tabungan (bukan kredit), bot juga mengirim
 * pesan tambahan bahwa nomor VA tersebut perlu didaftarkan secara manual ke kantor.
 * </p>
 */
@Component
public class VirtualAccountHandler {

	private final BillService billService;
	private final SavingsService savingsService;
	private final WhatsAppSenderService whatsAppSenderService;

	public VirtualAccountHandler(BillService billService, SavingsService savingsService, WhatsAppSenderService whatsAppSenderService) {
		this.billService = billService;
		this.savingsService = savingsService;
		this.whatsAppSenderService = whatsAppSenderService;
	}

	public record Account(String name, String accountNumber, String address) {}

	/**
	 * Mencari data akun berdasarkan nomor — bisa nomor SPK kredit atau nomor rekening tabungan.
	 * Kredit dicari duluan, kalau tidak ditemukan baru dicari ke tabungan.
	 *
	 * @param accountNumber nomor SPK atau nomor rekening yang akan dicari
	 * @return objek {@link Bills} atau {@link Savings} sesuai yang ditemukan, atau {@code null}
	 */
	public Object findAccount(String accountNumber) {
		Bills bills = billService.getBillById(accountNumber)
				.subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
				.block();
		if (bills != null) {
			return bills;
		}
		return savingsService.findById(accountNumber)
				.subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
				.block();
	}

	/**
	 * Mengambil detail akun (nama, nomor, alamat) yang dinormalisasi ke dalam record {@link Account}.
	 * Menangani dua sumber data berbeda: kredit ({@link Bills}) dan tabungan ({@link Savings}).
	 *
	 * @param accountNumber nomor SPK atau nomor rekening
	 * @return detail akun dalam format yang seragam, atau {@code null} kalau tidak ditemukan
	 */
	public Account getAccountDetails(String accountNumber) {
		Object account = findAccount(accountNumber);

		if (account instanceof Bills bills) {
			return new Account(
				bills.getName(),
				bills.getNoSpk(),
				bills.getAddress()
			);
		} else if (account instanceof Savings savings) {
			return new Account(
				savings.getName(),
				savings.getTabId(),
				savings.getAddress()
			);
		}

		return null;
	}

	/**
	 * Membuat pesan berisi informasi akun dan nomor VA dari empat bank sekaligus.
	 *
	 * @param accountNumber nomor SPK atau nomor rekening
	 * @return string pesan yang siap dikirim ke WhatsApp, atau pesan error kalau akun tidak ditemukan
	 */
	public String generateVirtualAccountMessage(String accountNumber) {
		Account account = getAccountDetails(accountNumber);

		if (account == null) {
			return "Nomor akun tidak ditemukan.";
		}


		return "*Informasi Akun*\n\n" +
			"No SPK: _" + account.accountNumber() + "_\n" +
			"Nama: _" + account.name() + "_\n" +
			"Alamat: _" + account.address() + "_\n\n" +


			"*Virtual Account Numbers*\n\n" +
			generateMandiriVA(account.accountNumber()) + "\n\n" +
			generateBriVA(account.accountNumber()) + "\n\n" +
			generateDanamonVA(account.accountNumber()) + "\n\n" +
			generateBniVA(account.accountNumber());
	}

	private String generateMandiriVA(String accountNumber) {
		String formatted = formatAccountNumber(accountNumber, "86219 1 ");
		return "🏦 *Mandiri*\n" + formatted;
	}

	private String generateBriVA(String accountNumber) {
		String formatted = formatAccountNumber(accountNumber, "14654 ");
		return "🏦 *BRI (BRIVA)*\n" + formatted;
	}

	private String generateDanamonVA(String accountNumber) {
		if (accountNumber.length() < 12) {
			return "🏦 *Danamon*\nFormat nomor tidak valid";
		}

		String va = "7997 " +
			accountNumber.substring(0, 4) + " " +
			accountNumber.substring(4, 6) + " " +
			accountNumber.substring(6, 12);
		return "🏦 *Danamon*\n" + va;
	}

	/**
	 * Format nomor Virtual Account BNI
	 */
	private String generateBniVA(String accountNumber) {
		if (accountNumber.length() < 12) {
			return "🏦 *BNI*\nFormat nomor tidak valid";
		}

		String va = "8743 " +
			accountNumber.substring(0, 4) + " " +
			accountNumber.substring(4, 6) + " " +
			accountNumber.substring(6, 12);
		return "🏦 *BNI*\n" + va;
	}

	/**
	 * Helper method untuk format nomor akun
	 */
	private String formatAccountNumber(String accountNumber, String prefix) {
		if (accountNumber.length() < Math.max(4, 12)) {
			return prefix + "Format nomor tidak valid";
		}

		return prefix +
			accountNumber.substring(0, 4) + " " +
			accountNumber.substring(6, 12);
	}
	public void handler(WhatsAppWebhookDTO webhook) {
		whatsAppSenderService.sendWhatsAppText(webhook.buildChatId(), generateVirtualAccountMessage(webhook.getPayload().getBody().substring(".va ".length()))).subscribe();
		if (findAccount(webhook.getPayload().getBody().substring(".va ".length())) instanceof Savings) {
			whatsAppSenderService.sendWhatsAppText(webhook.buildChatId(), "Nomor _Virtual Account_ tersebut harus didaftarkan secara manual").subscribe();
		}
	}
}