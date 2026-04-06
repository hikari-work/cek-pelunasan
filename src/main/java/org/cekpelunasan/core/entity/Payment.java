package org.cekpelunasan.core.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Data transaksi pembayaran yang dilakukan oleh pengguna bot.
 * <p>
 * Berbeda dengan {@link Paying} yang hanya menyimpan flag lunas/belum, class ini
 * menyimpan detail transaksi pembayaran — termasuk berapa nominal yang dibayar
 * dan oleh siapa. Ini digunakan untuk keperluan pencatatan pembayaran yang lebih
 * rinci, misalnya ketika AO mengonfirmasi setoran nasabah lewat bot.
 * </p>
 * <p>
 * Koleksi MongoDB yang dipakai adalah {@code payment}.
 * </p>
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collection = "payment")
public class Payment {

	/**
	 * ID unik transaksi pembayaran ini, di-generate otomatis oleh MongoDB.
	 */
	@Id
	private String id;

	/**
	 * Nominal pembayaran dalam satuan rupiah.
	 */
	private Long amount;

	/**
	 * Identitas pengguna bot (biasanya chat ID Telegram atau kode AO) yang
	 * mencatat transaksi pembayaran ini.
	 */
	private String user;

	/**
	 * Tanda apakah transaksi ini sudah selesai diproses. Bernilai {@code true}
	 * jika pembayaran sudah dikonfirmasi, {@code false} jika masih pending.
	 */
	private boolean isPaid;
}
