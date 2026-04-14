package org.cekpelunasan.core.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Menyimpan waktu terakhir data diperbarui untuk setiap jenis koleksi (tagihan, tabungan, dll).
 * Digunakan untuk menampilkan warning ke user jika data yang ditampilkan bukan dari hari ini.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collection = "data_update_log")
public class DataUpdateLog {

	/**
	 * Nama jenis data, misalnya "TAGIHAN" atau "SAVING". Sekaligus menjadi primary key.
	 */
	@Id
	private String dataType;

	/**
	 * Waktu terakhir data diperbarui, dalam zona waktu UTC+7 (WIB).
	 */
	private LocalDateTime updatedAt;
}
