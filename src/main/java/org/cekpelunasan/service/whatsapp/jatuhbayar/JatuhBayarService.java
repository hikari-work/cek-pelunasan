package org.cekpelunasan.service.whatsapp.jatuhbayar;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.dto.whatsapp.webhook.WhatsAppWebhookDTO;
import org.cekpelunasan.entity.Bills;
import org.cekpelunasan.entity.Savings;
import org.cekpelunasan.service.Bill.BillService;
import org.cekpelunasan.service.savings.SavingsService;
import org.cekpelunasan.service.whatsapp.sender.WhatsAppSenderService;
import org.cekpelunasan.utils.RupiahFormatUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JatuhBayarService {

	private final static String BRANCH_CODE = "1075";

	private final BillService billService;
	private final RupiahFormatUtils rupiahFormatUtils;
	private final SavingsService savingsService;
	private final WhatsAppSenderService whatsAppSenderService;

	@Async
	@SuppressWarnings("UnusedReturnValue")
	public CompletableFuture<Void> handle(WhatsAppWebhookDTO command) {
		Map<String, List<Bills>> dataJatuhBayar = getBills();

		dataJatuhBayar.forEach((accountOfficer, bills) -> {
			if (!bills.isEmpty()) {
				String message = formatJatuhBayar(bills, accountOfficer);
				whatsAppSenderService.deleteMessage(command.buildChatId(), command.getMessage().getId());
				whatsAppSenderService.sendWhatsAppText(command.buildChatId(), message, null);
				System.out.println("Pesan untuk " + accountOfficer + ":\n" + bills.size() + " nasabah");
			}
		});

		return CompletableFuture.completedFuture(null);
	}

	public Map<String, List<Bills>> getBills() {
		LocalDate today = LocalDate.now();
		String dayOfMonth = String.valueOf(today.getDayOfMonth());

		return billService.findAllBillsByBranch(BRANCH_CODE)
			.stream()
			.filter(bill -> bill.getPayDown().equals(dayOfMonth))
			.filter(bill -> !bill.getAccountOfficer().equals("JKS"))
			.filter(bill -> !bill.getAccountOfficer().equals("AIN"))
			.collect(Collectors.groupingBy(Bills::getAccountOfficer));
	}

	public String formatJatuhBayar(List<Bills> bills, String ao) {
		if (bills == null || bills.isEmpty()) {
			return "";
		}

		LocalDate today = LocalDate.now();
		StringBuilder builder = new StringBuilder();
		builder.append("🔔 *REMINDER JATUH BAYAR*\n");
		builder.append("📅 Tanggal: ").append(today).append("\n");
		builder.append("👤 AO: *").append(ao).append("*\n");
		builder.append("📊 Total Nasabah: ").append(bills.size()).append(" orang\n\n");

		for (int i = 0; i < bills.size(); i++) {
			Bills bill = bills.get(i);
			builder.append("*").append(i + 1).append(". ").append(bill.getName()).append("*\n");
			builder.append("   💳 No SPK : ").append(bill.getNoSpk()).append("\n");
			builder.append("   💰 Angsuran: ").append(rupiahFormatUtils.formatRupiah(bill.getInstallment())).append("\n");

			if (bill.getLastInstallment() != null && bill.getLastInstallment().compareTo(0L) > 0) {
				builder.append("   ⚠️ Tunggakan: ").append(rupiahFormatUtils.formatRupiah(bill.getLastInstallment())).append("\n");
			}

			try {
				Savings savings = savingsService.findByCif(bill.getCustomerId());
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

		// Footer
		builder.append("═══════════════════\n");
		builder.append("Harap segera lakukan follow up kepada nasabah terkait.\n");
		builder.append("_Pesan otomatis dari sistem_");

		return builder.toString();
	}
	public String getSummaryMessage() {
		Map<String, List<Bills>> dataJatuhBayar = getBills();
		LocalDate today = LocalDate.now();

		if (dataJatuhBayar.isEmpty()) {
			return "✅ Tidak ada nasabah yang jatuh bayar hari ini (" + today + ")";
		}

		StringBuilder summary = new StringBuilder();
		summary.append("📊 *SUMMARY JATUH BAYAR*\n");
		summary.append("📅 Tanggal: ").append(today).append("\n\n");

		int totalNasabah = 0;
		for (Map.Entry<String, List<Bills>> entry : dataJatuhBayar.entrySet()) {
			String ao = entry.getKey();
			int jumlahNasabah = entry.getValue().size();
			totalNasabah += jumlahNasabah;

			summary.append("👤 ").append(ao).append(": ").append(jumlahNasabah).append(" nasabah\n");
		}

		summary.append("\n📈 *Total: ").append(totalNasabah).append(" nasabah*");
		return summary.toString();
	}

	private boolean isValidBill(Bills bill) {
		return bill != null
			&& bill.getName() != null && !bill.getName().trim().isEmpty()
			&& bill.getCustomerId() != null && !bill.getCustomerId().trim().isEmpty()
			&& bill.getAccountOfficer() != null && !bill.getAccountOfficer().trim().isEmpty()
			&& bill.getInstallment() != null;
	}
	public Map<String, List<Bills>> getValidBills() {
		LocalDate today = LocalDate.now();
		String dayOfMonth = String.valueOf(today.getDayOfMonth());

		return billService.findAllBillsByBranch(BRANCH_CODE)
			.stream()
			.filter(bill -> bill.getPayDown().equals(dayOfMonth))
			.filter(this::isValidBill)
			.collect(Collectors.groupingBy(Bills::getAccountOfficer));
	}
}