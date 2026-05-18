package org.cekpelunasan.platform.whatsapp.service.jatuhbayar;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.platform.whatsapp.dto.webhook.WhatsAppWebhookDTO;
import org.cekpelunasan.core.entity.Bills;
import org.cekpelunasan.core.entity.Savings;
import org.cekpelunasan.core.service.bill.BillService;
import org.cekpelunasan.core.service.savings.SavingsService;
import org.cekpelunasan.platform.whatsapp.service.sender.WhatsAppSenderService;
import org.cekpelunasan.utils.RupiahFormatUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Mengirimkan pengingat jatuh bayar harian kepada Account Officer lewat WhatsApp.
 * <p>
 * Service ini membaca semua tagihan dari cabang Kaligondang (kode 1075) dan menyaring
 * nasabah yang tanggal bayarnya jatuh hari ini. Hasilnya dikelompokkan per AO, lalu
 * dikirimkan satu per satu sebagai pesan WhatsApp.
 * </p>
 * <p>
 * Pesan pertama dikirim dengan cara update (edit) pesan yang memicu perintah,
 * sementara pesan AO berikutnya dikirim sebagai pesan baru dengan jeda 1 detik
 * di antara setiap pengiriman untuk menghindari rate limit dari gateway.
 * Setiap pesan juga menyertakan nomor HP nasabah yang diambil dari data tabungan.
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JatuhBayarService {


	private final static String BRANCH_CODE = "1075";

	private final BillService billService;
	private final RupiahFormatUtils rupiahFormatUtils;
	private final SavingsService savingsService;
	private final WhatsAppSenderService whatsAppSenderService;

	/**
	 * Memulai proses pengiriman pengingat jatuh bayar secara asinkron.
	 * <p>
	 * Mengambil daftar tagihan yang jatuh hari ini, mengelompokkannya per AO,
	 * lalu mengirimkan pesan ke setiap AO secara berurutan dengan jeda 1 detik.
	 * </p>
	 *
	 * @param command data webhook dari perintah yang memicu pengiriman reminder
	 * @return CompletableFuture yang selesai setelah semua pesan terkirim
	 */
	@Async
	@SuppressWarnings("UnusedReturnValue")
	public CompletableFuture<Void> handle(WhatsAppWebhookDTO command) {
		return CompletableFuture
			.supplyAsync(this::getBills)
			.thenAccept(dataJatuhBayar -> {
				AtomicBoolean isFirst = new AtomicBoolean(true);
				dataJatuhBayar.forEach((accountOfficer, bills) -> {
					if (!bills.isEmpty()) {
						String message = formatJatuhBayar(bills, accountOfficer);
						if (isFirst.getAndSet(false)) {
							whatsAppSenderService.updateMessage(
								command.buildChatId(),
								command.getPayload().getId(),
								message
							).subscribe();
						} else {
							whatsAppSenderService.sendWhatsAppText(
								command.buildChatId(),
								message,
								null
							).subscribe();
						}
						try {
							Thread.sleep(1000);
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
							throw new RuntimeException("Thread interrupted", e);
						}
					}
				});
			})
			.exceptionally(throwable -> {
				log.error("Error processing bills: ", throwable);
				return null;
			});
	}

	/**
	 * Mengambil semua tagihan hari ini dan mengelompokkannya per Account Officer.
	 * <p>
	 * Membaca semua tagihan dari cabang 1075 (Kaligondang), kemudian menyaring
	 * yang tanggal bayarnya sama dengan hari ini (berdasarkan tanggal dalam bulan).
	 * Hasilnya adalah Map dengan nama AO sebagai key dan daftar tagihannya sebagai value.
	 * </p>
	 *
	 * @return peta tagihan hari ini yang sudah dikelompokkan per nama AO
	 */
	public Map<String, List<Bills>> getBills() {
		LocalDate today = LocalDate.now(ZoneOffset.ofHours(7));
		String dayOfMonth = String.valueOf(today.getDayOfMonth());

		return billService.findAllBillsByBranch(BRANCH_CODE)
				.subscribeOn(Schedulers.boundedElastic())
				.block()
			.stream()
			.filter(bill -> bill.getPayDown().equals(dayOfMonth))
			.collect(Collectors.groupingBy(Bills::getAccountOfficer));
	}

	/**
	 * Memformat daftar tagihan menjadi pesan teks yang siap dikirim ke WhatsApp.
	 * <p>
	 * Pesan mencakup header berisi tanggal dan nama AO, diikuti daftar nasabah
	 * beserta nomor SPK, jumlah angsuran (atau tunggakan jika ada), dan nomor HP
	 * yang diambil langsung dari data tabungan secara real-time.
	 * </p>
	 *
	 * @param bills daftar tagihan yang akan diformat
	 * @param ao    nama Account Officer pemilik tagihan ini
	 * @return string pesan yang sudah diformat dan siap dikirim, atau string kosong jika daftar kosong
	 */
	public String formatJatuhBayar(List<Bills> bills, String ao) {
		if (bills == null || bills.isEmpty()) {
			return "";
		}

		LocalDate today = LocalDate.now(ZoneOffset.ofHours(7));
		StringBuilder builder = new StringBuilder();
		builder.append("🔔 *REMINDER JATUH BAYAR*\n");
		builder.append("📅 Tanggal: ").append(today).append("\n");
		builder.append("👤 AO: *").append(ao).append("*\n");
		builder.append("📊 Total Nasabah: ").append(bills.size()).append(" orang\n\n");

		for (int i = 0; i < bills.size(); i++) {
			Bills bill = bills.get(i);
			builder.append("*").append(i + 1).append(". ").append(bill.getName()).append("*\n");
			builder.append("   💳 No SPK : ").append(bill.getNoSpk()).append("\n");

			if (bill.getLastInstallment() != null && bill.getLastInstallment().compareTo(0L) > 0) {
				builder.append("   ⚠️ Tunggakan: ").append(rupiahFormatUtils.formatRupiah(bill.getLastInstallment())).append("\n");
			} else {
				builder.append("   💰 Angsuran: ").append(rupiahFormatUtils.formatRupiah(bill.getInstallment())).append("\n");
			}

			try {
				Savings savings = savingsService.findByCif(bill.getCustomerId())
						.subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
						.block();
				if (savings != null && savings.getPhone() != null && !savings.getPhone().isEmpty()) {
					builder.append("   📱 No HP: ").append(savings.getPhone()).append("\n");
				} else {
					builder.append("   📱 No HP: -\n");
				}
			} catch (Exception e) {
				builder.append("   📱 No HP: Error retrieving\n");
			}

			builder.append("\n");
		}

		builder.append("═══════════════════\n");
		builder.append("Harap segera lakukan follow up kepada nasabah terkait.\n");
		builder.append("_Pesan otomatis dari sistem_");

		return builder.toString();
	}

}