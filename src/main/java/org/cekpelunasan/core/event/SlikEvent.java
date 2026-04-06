package org.cekpelunasan.core.event;

import org.springframework.context.ApplicationEvent;

/**
 * Event yang dipublikasikan ketika ada proses pengecekan SLIK (Sistem Layanan Informasi Keuangan)
 * yang perlu diketahui oleh komponen lain dalam aplikasi.
 * <p>
 * SLIK adalah sistem OJK yang digunakan untuk mengecek riwayat kredit calon nasabah.
 * Event ini dipublikasikan untuk memberi tahu bagian lain dari sistem — misalnya untuk
 * mencatat riwayat pengecekan atau memicu proses lanjutan setelah pengecekan SLIK selesai.
 * </p>
 * <p>
 * Cara pakainya: {@code applicationEventPublisher.publishEvent(new SlikEvent(this))}.
 * </p>
 */
public class SlikEvent extends ApplicationEvent {

	/**
	 * Membuat event SLIK baru dengan objek sumber yang memicunya.
	 *
	 * @param source objek yang memicu event ini, biasanya class yang menginisiasi
	 *               proses pengecekan SLIK (tidak boleh {@code null})
	 */
	public SlikEvent(Object source) {
		super(source);
	}
}
