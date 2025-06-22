package org.cekpelunasan.utils;


import org.cekpelunasan.entity.Bills;
import org.cekpelunasan.entity.Repayment;
import org.cekpelunasan.service.simulasi.SimulasiService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
public class TagihanUtils {

	private final RupiahFormatUtils rupiahFormatUtils;
	private final SimulasiService simulasiService;

	public TagihanUtils(RupiahFormatUtils rupiahFormatUtils, SimulasiService simulasiService1) {
		this.rupiahFormatUtils = rupiahFormatUtils;
		this.simulasiService = simulasiService1;
	}

	public String detailBills(Bills bill) {
		Map<String, Integer> totalKeterlambatan = simulasiService.findTotalKeterlambatan(bill.getNoSpk());
		return String.format("""
            📄 *Detail Kredit*

            👤 *Nasabah*
            • Nama: *%s*
            • No SPK: `%s`
            • Alamat: %s
            • Produk: %s

            💳 *Pinjaman*
            • Plafond: %s
            • Baki Debet: %s
            • Realisasi: %s
            • Jatuh Tempo: %s

            💹 *Angsuran*
            • Bunga: %s
            • Pokok: %s
            • Total: %s

            ⚠️ *Tunggakan*
            • Bunga: %s x %s
            • Pokok: %s x %s

            📊 *Status*
            • Hari Tunggakan: %d hari
            • Kolektibilitas: %s

            💸 *Tagihan*
            • Total: %s
            • Min. Pokok: %s
            • Min. Bunga: %s

            👨‍💼 *AO*: %s
            """,
			bill.getName(),
			bill.getNoSpk(),
			bill.getAddress(),
			bill.getProduct(),
			rupiahFormatUtils.formatRupiah(bill.getPlafond()),
			rupiahFormatUtils.formatRupiah(bill.getDebitTray()),
			bill.getRealization(),
			bill.getDueDate(),
			rupiahFormatUtils.formatRupiah(bill.getInterest()),
			rupiahFormatUtils.formatRupiah(bill.getPrincipal()),
			rupiahFormatUtils.formatRupiah(bill.getInstallment()),
			totalKeterlambatan.get("I"),
			rupiahFormatUtils.formatRupiah(bill.getLastInterest()),
			totalKeterlambatan.get("P"),
			rupiahFormatUtils.formatRupiah(bill.getLastPrincipal()),
			Integer.parseInt(bill.getDayLate()),
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
            📄 ID SPK: `%s`
            📍 Alamat: %s

            📅 *Tempo*
            • Jatuh Tempo: %s

            💰 *Tagihan*
            • Total: %s

            👨‍💼 *Account Officer*
            • AO: %s

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
