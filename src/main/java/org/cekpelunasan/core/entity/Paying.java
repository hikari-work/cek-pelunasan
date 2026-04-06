package org.cekpelunasan.core.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Penanda apakah sebuah tagihan sudah lunas atau belum.
 * <p>
 * Class ini sederhana tapi penting — dipakai sebagai flag status pelunasan
 * untuk setiap nomor SPK. Ketika nasabah melunasi tagihannya, sebuah dokumen
 * {@code Paying} dibuat atau diperbarui dengan {@code paid = true}.
 * </p>
 * <p>
 * ID yang dipakai di sini biasanya adalah nomor SPK dari tagihan terkait,
 * sehingga mudah dicari secara langsung. Koleksi MongoDB: {@code paying}.
 * </p>
 */
@Document(collection = "paying")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Paying {

	/**
	 * ID dokumen ini — biasanya berisi nomor SPK tagihan yang bersangkutan,
	 * sehingga pengecekan status lunas bisa dilakukan langsung pakai nomor SPK.
	 */
	@Id
	private String id;

	/**
	 * Status pelunasan tagihan. Bernilai {@code true} jika tagihan sudah dilunasi,
	 * {@code false} atau {@code null} jika belum.
	 */
	private Boolean paid;
}
