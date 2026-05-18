package org.cekpelunasan.core.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Event yang dipublikasikan ke seluruh sistem ketika proses update database selesai dikerjakan.
 * <p>
 * Setiap kali ada proses import atau pembaruan data (misalnya data tagihan, tabungan,
 * atau kolek tas), class ini digunakan untuk memberi tahu komponen lain bahwa ada sesuatu
 * yang berubah — berhasil atau tidak. Listener seperti {@link DatabaseUpdateListener}
 * akan menangkap event ini dan mengirim notifikasi ke semua pengguna terdaftar.
 * </p>
 * <p>
 * Cara pakainya: publish lewat {@code ApplicationEventPublisher.publishEvent(new DatabaseUpdateEvent(this, eventType, true))}.
 * </p>
 */
public class DatabaseUpdateEvent extends ApplicationEvent {

	/**
	 * Jenis database yang baru saja diperbarui, misalnya tagihan, tabungan, atau kolek tas.
	 * Menentukan teks yang akan muncul di pesan notifikasi.
	 */
	@Getter
	private final EventType eventType;

	/**
	 * Status keberhasilan proses update. Bernilai {@code true} jika update berjalan lancar,
	 * {@code false} jika gagal karena error.
	 */
	@Getter
	private final boolean isSuccess;

	/**
	 * Membuat event baru dengan informasi jenis database yang diupdate dan status hasilnya.
	 *
	 * @param source    objek yang memicu event ini (biasanya class yang memanggil import)
	 * @param eventType jenis database yang baru saja diperbarui
	 * @param isSuccess {@code true} jika update berhasil, {@code false} jika gagal
	 */
	public DatabaseUpdateEvent(Object source, EventType eventType, boolean isSuccess) {
		super(source);
		this.eventType = eventType;
		this.isSuccess = isSuccess;
	}
}
