package org.cekpelunasan.core.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Representasi satu baris data tagihan nasabah yang tersimpan di koleksi MongoDB {@code tagihan}.
 * <p>
 * Class ini dipakai untuk dua keperluan utama: menampilkan informasi tagihan yang belum lunas
 * (termasuk berapa yang harus dibayar bulan ini), dan menghitung angka pelunasan jika nasabah
 * ingin melunasi kreditnya lebih awal.
 * </p>
 * <p>
 * Data di sini bersumber dari sistem core banking dan diperbarui secara berkala oleh proses
 * import data. Jangan mengubah field ini secara manual kecuali lewat mekanisme import resmi.
 * </p>
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collection = "tagihan")
public class Bills {

	/**
	 * Nomor identitas unik nasabah di sistem core banking.
	 */
	private String customerId;

	/**
	 * Kode wilayah tempat nasabah ini terdaftar, misalnya "WILAYAH I" atau "JABODETABEK".
	 */
	private String wilayah;

	/**
	 * Kode cabang yang mengelola tagihan ini, misalnya "1075".
	 */
	private String branch;

	/**
	 * Nomor SPK (Surat Perintah Kerja) yang sekaligus menjadi primary key dokumen ini.
	 * Setiap tagihan punya nomor SPK yang unik.
	 */
	@Id
	private String noSpk;

	/**
	 * Nama atau kode lokasi kantor yang menangani tagihan ini.
	 */
	private String officeLocation;

	/**
	 * Nama produk kredit yang diambil oleh nasabah, misalnya "KUR Mikro" atau "Kredit Usaha".
	 */
	private String product;

	/**
	 * Nama lengkap nasabah.
	 */
	private String name;

	/**
	 * Alamat nasabah sesuai data yang tercatat di sistem.
	 */
	private String address;

	/**
	 * Status atau nominal uang muka yang telah dibayarkan. Nilainya bisa berupa angka
	 * atau teks status tertentu tergantung format dari core banking.
	 */
	private String payDown;

	/**
	 * Status atau tanggal realisasi kredit ini. Menunjukkan kapan kredit ini
	 * benar-benar dicairkan ke nasabah.
	 */
	private String realization;

	/**
	 * Tanggal jatuh tempo angsuran bulan ini dalam format teks.
	 */
	private String dueDate;

	/**
	 * Status kolektibilitas kredit ini, misalnya "1" (lancar), "2" (dalam perhatian khusus),
	 * hingga "5" (macet). Semakin besar angkanya, semakin bermasalah kreditnya.
	 */
	private String collectStatus;

	/**
	 * Jumlah hari keterlambatan pembayaran angsuran, dalam satuan hari.
	 */
	private String dayLate;

	/**
	 * Plafond kredit yang disetujui, yaitu batas maksimal pinjaman nasabah ini dalam rupiah.
	 */
	private Long plafond;

	/**
	 * Sisa pokok pinjaman yang masih harus dilunasi (outstanding pokok), dalam rupiah.
	 */
	private Long debitTray;

	/**
	 * Total bunga yang terakumulasi sampai saat ini dan belum dibayar, dalam rupiah.
	 */
	private Long interest;

	/**
	 * Jumlah pokok pinjaman yang harus dibayar pada angsuran bulan ini, dalam rupiah.
	 */
	private Long principal;

	/**
	 * Total angsuran bulan ini (pokok + bunga), dalam rupiah.
	 */
	private Long installment;

	/**
	 * Bunga dari angsuran terakhir yang sudah jatuh tempo tapi belum dibayar, dalam rupiah.
	 */
	private Long lastInterest;

	/**
	 * Pokok dari angsuran terakhir yang sudah jatuh tempo tapi belum dibayar, dalam rupiah.
	 */
	private Long lastPrincipal;

	/**
	 * Total angsuran terakhir yang sudah jatuh tempo tapi belum dibayar, dalam rupiah.
	 */
	private Long lastInstallment;

	/**
	 * Angka total yang harus dibayar jika nasabah ingin melunasi kreditnya sekarang,
	 * mencakup semua komponen (pokok, bunga, denda), dalam rupiah.
	 */
	private Long fullPayment;

	/**
	 * Minimal bunga yang harus dibayar agar tagihan ini tidak dianggap macet, dalam rupiah.
	 */
	private Long minInterest;

	/**
	 * Minimal pokok yang harus dibayar agar tagihan ini tidak dianggap macet, dalam rupiah.
	 */
	private Long minPrincipal;

	/**
	 * Denda bunga akibat keterlambatan pembayaran, dihitung berdasarkan hari telat, dalam rupiah.
	 */
	private Long penaltyInterest;

	/**
	 * Denda pokok akibat keterlambatan pembayaran, dalam rupiah.
	 */
	private Long penaltyPrincipal;

	/**
	 * Kode atau nama Account Officer yang bertanggung jawab atas tagihan nasabah ini.
	 */
	private String accountOfficer;

	/**
	 * Nama atau kode kios tempat nasabah ini terdaftar, digunakan khusus untuk
	 * cabang yang punya sistem kios/loket.
	 */
	private String kios;

	/**
	 * Jumlah uang titipan nasabah yang sudah ada di sistem tapi belum diaplikasikan
	 * ke angsuran, dalam rupiah.
	 */
	private Long titipan;

	/**
	 * Bunga tetap (flat interest) yang dikenakan, dalam rupiah.
	 */
	private Long fixedInterest;
}
