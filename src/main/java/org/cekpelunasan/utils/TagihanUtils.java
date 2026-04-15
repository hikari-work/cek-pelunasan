package org.cekpelunasan.utils;


import lombok.RequiredArgsConstructor;
import org.cekpelunasan.core.entity.Bills;
import org.cekpelunasan.core.service.log.DataUpdateLogService;
import org.cekpelunasan.core.service.simulasi.SimulasiService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Memformat data tagihan kredit menjadi teks detail atau kompak untuk ditampilkan di bot.
 * <p>
 * Menyediakan dua level detail:
 * <ul>
 *   <li>{@link #detailBills} — tampilan lengkap dengan data keterlambatan, kolektibilitas,
 *       dan rincian angsuran. Cocok untuk cek tagihan mendalam oleh AO.</li>
 *   <li>{@link #billsCompact} — tampilan ringkas dengan informasi utama saja.
 *       Cocok untuk daftar atau preview cepat.</li>
 * </ul>
 * </p>
 */
@Component
@RequiredArgsConstructor
public class TagihanUtils {

	private final RupiahFormatUtils rupiahFormatUtils;
	private final SimulasiService simulasiService;
	private final DataUpdateLogService dataUpdateLogService;

	/**
	 * Memformat detail lengkap tagihan kredit satu nasabah.
	 * <p>
	 * Mengambil data keterlambatan dan maksimal hari bayar dari {@link SimulasiService}
	 * secara real-time, sehingga informasi yang ditampilkan selalu terkini.
	 * Total tagihan dihitung berdasarkan tanggal realisasi: kalau realisasi hari ini,
	 * pakai full payment; kalau sudah lewat, pakai tunggakan; kalau belum, pakai angsuran rutin.
	 * </p>
	 *
	 * @param bill data tagihan kredit nasabah
	 * @return string detail tagihan lengkap yang siap dikirim
	 */
	public String detailBills(Bills bill) {
		Map<String, Integer> totalKeterlambatan = simulasiService.findTotalKeterlambatan(bill.getNoSpk())
				.subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
				.block();
		Long maxBayar = simulasiService.findMaxBayar(bill.getNoSpk())
				.subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
				.block();
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
			Integer.parseInt(String.valueOf(maxBayar)),
			bill.getCollectStatus(),
			rupiahFormatUtils.formatRupiah(calculateTotalPayment(bill)),
			rupiahFormatUtils.formatRupiah(bill.getMinPrincipal()),
			rupiahFormatUtils.formatRupiah(bill.getMinInterest()),
			bill.getAccountOfficer()
		) + dataUpdateLogService.telegramWarning("TAGIHAN");
	}


	/**
	 * Memformat tagihan dalam tampilan ringkas untuk preview atau daftar.
	 * Hanya menampilkan informasi paling penting: nama, ID SPK, alamat, jatuh tempo,
	 * total tagihan, nama AO, dan timestamp pembuatan pesan (WIB).
	 *
	 * @param bills data tagihan kredit nasabah
	 * @return string tagihan ringkas yang siap dikirim
	 */
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
			LocalDateTime.now(ZoneOffset.ofHours(7)).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
		) + dataUpdateLogService.telegramWarning("TAGIHAN");
	}
	private Long calculateTotalPayment(Bills bills) {
		String realization = bills.getRealization(); // "31-12-2025"
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
		LocalDate realizationDate = LocalDate.parse(realization, formatter);
		LocalDate today = LocalDate.now(ZoneOffset.ofHours(7));
		if (realizationDate.isEqual(today)) {
			return bills.getFullPayment();
		} else if (realizationDate.isBefore(today)) {
			return bills.getLastInterest() + bills.getLastPrincipal();
		} else {
			return bills.getInstallment();
		}
	}
}
