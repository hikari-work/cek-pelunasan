package org.cekpelunasan.service.whatsapp.dto;

import lombok.Data;

import java.text.NumberFormat;
import java.util.Locale;

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

	public Long getTotalPelunasan() {
		Long baki = this.bakiDebet != null ? this.bakiDebet : 0L;
		Long bunga = this.perhitunganBunga != null ? this.perhitunganBunga : 0L;
		Long penalti = this.penalty != null ? this.penalty : 0L;
		Long dendaVal = this.denda != null ? this.denda : 0L;

		return baki + bunga + penalti + dendaVal;
	}
	public String toTelegramMessage() {
		NumberFormat formatter = NumberFormat.getInstance(new Locale("id", "ID"));

		return """
        <b>👤 Nama   :</b> %s
        <b>📄 SPK    :</b> %s
        <b>🗺 Alamat :</b> %s
        
        <blockquote expandable><code><b>💰 DETAIL PELUNASAN</b>
        ┌────────────────────────────
        │ <b>Plafond           :</b> Rp %s
        │ <b>Baki Debet       :</b> Rp %s
        │ <b>%-16s:</b> Rp %s
        │ <b>Penalty %s x Bunga:</b> Rp %s
        │ <b>Denda            :</b> Rp %s
        └────────────────────────────
        <b>💵 Total Pelunasan: Rp %s</b>
        </code>
        <b>📅 JADWAL</b>
        <b>Tanggal Realisasi  :</b> %s
        <b>Tanggal Jatuh Tempo:</b> %s
        <b>Rencana Pelunasan  :</b> %s
        
        </blockquote>
        """.formatted(
			escapeHtml(this.nama != null ? this.nama : "-"),
			escapeHtml(this.spk != null ? this.spk : "-"),
			escapeHtml(this.alamat != null ? this.alamat : "-"),
			this.plafond != null ? formatter.format(this.plafond) : "0",
			this.bakiDebet != null ? formatter.format(this.bakiDebet) : "0",
			this.typeBunga,
			this.perhitunganBunga != null ? formatter.format(this.perhitunganBunga) : "0",
			this.multiplierPenalty,
			this.penalty != null ? formatter.format(this.penalty) : "0",
			this.denda != null ? formatter.format(this.denda) : "0",
			formatter.format(getTotalPelunasan()),
			escapeHtml(this.tglRealisasi != null ? this.tglRealisasi : "-"),
			escapeHtml(this.tglJatuhTempo != null ? this.tglJatuhTempo : "-"),
			escapeHtml(this.rencanaPelunasan != null ? this.rencanaPelunasan : "-")
		);
	}
	private String escapeHtml(String text) {
		if (text == null) return "";
		return text.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;")
			.replace("\"", "&quot;")
			.replace("'", "&#x27;");
	}
	public String toWhatsAppMessageClean() {
		NumberFormat formatter = NumberFormat.getInstance(new Locale("id", "ID"));

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
			sanitizeForWhatsApp(this.rencanaPelunasan != null ? this.rencanaPelunasan : "-")
		);
	}
	private String sanitizeForWhatsApp(String text) {
		if (text == null) return "-";

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
