package org.cekpelunasan.utils;


import org.cekpelunasan.entity.Bills;
import org.cekpelunasan.entity.Repayment;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class TagihanUtils {

	private final RupiahFormatUtils rupiahFormatUtils;

	public TagihanUtils(RupiahFormatUtils rupiahFormatUtils) {
		this.rupiahFormatUtils = rupiahFormatUtils;
	}

	public String detailBills(Bills bill) {
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
			rupiahFormatUtils.formatRupiah(bill.getPlafond()),
			rupiahFormatUtils.formatRupiah(bill.getDebitTray()),
			bill.getRealization(),
			bill.getDueDate(),
			rupiahFormatUtils.formatRupiah(bill.getInterest()),
			rupiahFormatUtils.formatRupiah(bill.getPrincipal()),
			rupiahFormatUtils.formatRupiah(bill.getInstallment()),
			rupiahFormatUtils.formatRupiah(bill.getLastInterest()),
			rupiahFormatUtils.formatRupiah(bill.getLastPrincipal()),
			bill.getDayLate(),
			bill.getCollectStatus(),
			rupiahFormatUtils.formatRupiah(bill.getFullPayment()),
			rupiahFormatUtils.formatRupiah(bill.getMinPrincipal()),
			rupiahFormatUtils.formatRupiah(bill.getMinInterest()),
			bill.getAccountOfficer()
		);
	}
	public String billsCompact(Bills bills) {
		return String.format("""
				🏦 *INFORMASI NASABAH*
				━━━━━━━━━━━━━━━━━━━
				
				👤 *%s*
				▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀
				
				📋 *Detail Nasabah*
				┌─────────────────
				│ 🔖 ID SPK: `%s`
				│ 📍 Alamat: %s
				└─────────────────
				
				📅 *Informasi Tempo*
				┌─────────────────
				│ 📆 Jatuh Tempo: %s
				└─────────────────
				
				💰 *Informasi Tagihan*
				┌─────────────────
				│ 💵 Total: %s
				└─────────────────
				
				👨‍💼 *Account Officer*
				┌─────────────────
				│ 👔 AO: %s
				└─────────────────
				
				⏱️ _Generated: %s_
				""",
			bills.getName(),
			bills.getNoSpk(),
			bills.getAddress(),
			bills.getPayDown(),
			String.format("Rp%,d,-", bills.getFullPayment()),
			bills.getAccountOfficer(),
			LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
		);
	}
	public String getAllPelunasan(Repayment dto) {
		return String.format("""
				🔷 *%s*
				┌────────────────────────
				│ 📎 *DATA NASABAH*
				│ └── 🔖 SPK    : `%s`
				│ └── 📍 Alamat : %s
				│
				│ 💳 *INFORMASI KREDIT*
				│ └── 💰 Plafond : %s
				└────────────────────────
				
				""",
			dto.getName(),
			dto.getCustomerId(),
			dto.getAddress(),
			rupiahFormatUtils.formatRupiah(dto.getPlafond())
		);
	}
}
