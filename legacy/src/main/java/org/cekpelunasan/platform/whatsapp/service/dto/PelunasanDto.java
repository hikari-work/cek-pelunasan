package org.cekpelunasan.platform.whatsapp.service.dto;

import lombok.Data;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Wadah data hasil perhitungan pelunasan kredit yang siap ditampilkan ke pengguna.
 * <p>
 * Class ini menampung semua informasi yang dibutuhkan untuk membuat pesan pelunasan:
 * data nasabah (nama, SPK, alamat), rincian keuangan (plafond, baki debet, bunga,
 * penalty, denda), dan tanggal-tanggal penting (realisasi, jatuh tempo, rencana lunas).
 * </p>
 * <p>
 * Dua method utama di sini adalah {@link #getTotalPelunasan()} untuk menghitung
 * total yang harus dibayar, dan {@link #toWhatsAppMessageClean()} untuk mengubah
 * data tersebut menjadi pesan yang siap dikirim lewat WhatsApp dengan format yang rapi.
 * </p>
 */
@Data
public class PelunasanDto {

	private String nama;
	private String spk;
	private String alamat;
	private Long plafond;
	private Long bakiDebet;
	private String tglRealisasi;
	private String tglJatuhTempo;
	private String rencanaPelunasan;
	private Long perhitunganBunga;
	private Long penalty;
	private Integer multiplierPenalty;
	private Long denda;
	private String typeBunga;

	/**
	 * Menghitung total yang harus dibayar nasabah saat pelunasan.
	 * <p>
	 * Rumusnya: baki debet + perhitungan bunga + penalty + denda.
	 * Setiap komponen yang null dianggap 0 supaya tidak error.
	 * </p>
	 *
	 * @return total pelunasan dalam satuan rupiah
	 */
	public Long getTotalPelunasan() {
		Long baki = this.bakiDebet != null ? this.bakiDebet : 0L;
		Long bunga = this.perhitunganBunga != null ? this.perhitunganBunga : 0L;
		Long penalti = this.penalty != null ? this.penalty : 0L;
		Long dendaVal = this.denda != null ? this.denda : 0L;

		return baki + bunga + penalti + dendaVal;
	}

	/**
	 * Mengubah data pelunasan menjadi pesan teks yang siap dikirim ke WhatsApp.
	 * <p>
	 * Pesan sudah diformat dengan bold, emoji, dan pemisah visual supaya mudah dibaca
	 * di layar HP. Semua angka diformat dalam format Rupiah Indonesia, dan karakter
	 * yang bisa merusak formatting WhatsApp (*, _, ~, `) di-escape otomatis.
	 * </p>
	 *
	 * @return string pesan pelunasan yang siap dikirim
	 */
	public String toWhatsAppMessageClean() {
		NumberFormat formatter = NumberFormat.getInstance(Locale.of("id", "ID"));

		return """
				*DETAIL PELUNASAN KREDIT*
				═══════════════════════

				*👤 NASABAH*
				Nama    : %s
				SPK     : %s
				Alamat  : %s

				*💰 PERHITUNGAN*
				Plafond         : `Rp %s`
				Baki Debet      : `Rp %s`
				%s    : `Rp %s`
				Penalty (%sx)   : `Rp %s`
				Denda           : `Rp %s`
				─────────────────────
				*TOTAL PELUNASAN : Rp %s*

				*📅 TANGGAL PENTING*
				Realisasi       : %s
				Jatuh Tempo     : %s
				Rencana Lunas   : %s
				""".formatted(
				sanitizeForWhatsApp(this.nama != null ? this.nama : "-"),
				sanitizeForWhatsApp(this.spk != null ? this.spk : "-"),
				sanitizeForWhatsApp(this.alamat != null ? this.alamat : "-"),
				this.plafond != null ? formatter.format(this.plafond) : "0",
				this.bakiDebet != null ? formatter.format(this.bakiDebet) : "0",
				this.typeBunga,
				this.perhitunganBunga != null ? formatter.format(this.perhitunganBunga) : "0",
				this.multiplierPenalty,
				this.penalty != null ? formatter.format(this.penalty) : "0",
				this.denda != null ? formatter.format(this.denda) : "0",
				formatter.format(getTotalPelunasan()),
				sanitizeForWhatsApp(this.tglRealisasi != null ? this.tglRealisasi : "-"),
				sanitizeForWhatsApp(this.tglJatuhTempo != null ? this.tglJatuhTempo : "-"),
				sanitizeForWhatsApp(this.rencanaPelunasan != null ? this.rencanaPelunasan : "-"));
	}

	private String sanitizeForWhatsApp(String text) {
		if (text == null)
			return "-";

		return text
				// Escape WhatsApp markdown characters
				.replace("*", "\\*")
				.replace("_", "\\_")
				.replace("~", "\\~")
				.replace("`", "\\`")
				// Clean up problematic characters
				.replace("\n", " ")
				.replace("\r", "")
				.trim();
	}
}
