package org.cekpelunasan.core.event;

/**
 * Daftar jenis database yang bisa diperbarui dan dipantau lewat sistem event.
 * <p>
 * Setiap nilai di enum ini merepresentasikan satu jenis koleksi data yang ada
 * di MongoDB. Ketika proses import data selesai, jenis database ini dikirimkan
 * bersama {@link DatabaseUpdateEvent} agar pesan notifikasi ke pengguna
 * menampilkan nama database yang tepat dan mudah dipahami.
 * </p>
 */
public enum EventType {

	/**
	 * Pembaruan data tabungan nasabah dari koleksi {@code savings}.
	 * Teks yang ditampilkan di notifikasi: "Tabungan".
	 */
	SAVING("Tabungan"),

	/**
	 * Pembaruan data daftar kunjungan penagihan dari koleksi {@code kolek_tas}.
	 * Teks yang ditampilkan di notifikasi: "Kolek Tas".
	 */
	KOLEK_TAS("Kolek Tas"),

	/**
	 * Pembaruan data tagihan kredit nasabah dari koleksi {@code tagihan}.
	 * Teks yang ditampilkan di notifikasi: "Tagihan".
	 */
	TAGIHAN("Tagihan");

	/**
	 * Label yang mudah dibaca manusia untuk jenis event ini.
	 * Nilai ini langsung dipakai dalam teks pesan notifikasi Telegram.
	 */
	public final String value;

	/**
	 * Membuat konstanta EventType dengan label yang diberikan.
	 *
	 * @param value teks label yang akan ditampilkan di notifikasi pengguna
	 */
	EventType(String value) {
		this.value = value;
	}
}
