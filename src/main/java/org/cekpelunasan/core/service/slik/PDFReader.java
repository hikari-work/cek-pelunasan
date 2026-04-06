package org.cekpelunasan.core.service.slik;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Membaca file PDF dan mengekstrak nomor identitas (NIK KTP) dari isinya.
 * Class ini digunakan dalam proses penanganan dokumen SLIK: setelah pengguna
 * mengunggah file PDF KTP, nomor 16 digit di dalamnya diekstrak otomatis
 * agar tidak perlu diketik manual.
 *
 * <p>Seluruh proses pembacaan PDF dijalankan di thread terpisah
 * ({@link Schedulers#boundedElastic()}) karena operasi I/O ini bersifat
 * blocking dan tidak boleh memblokir event loop reaktif.</p>
 */
@Slf4j
@Component
public class PDFReader {

	/**
	 * Mengekstrak nomor identitas 16 digit dari byte array PDF. Proses
	 * membaca seluruh teks dari PDF menggunakan PDFBox, lalu mencari pola
	 * angka 16 digit dengan regex. Nomor pertama yang ditemukan yang dikembalikan.
	 *
	 * <p>Jika PDF tidak mengandung angka 16 digit, atau terjadi error saat
	 * membaca PDF, method ini mengembalikan {@link Mono#empty()} agar pemanggil
	 * bisa menangani kasus tersebut.</p>
	 *
	 * @param object byte array yang merupakan isi file PDF
	 * @return {@link Mono} berisi nomor identitas 16 digit, atau kosong jika tidak ditemukan
	 */
	public Mono<String> generateIDNumber(byte[] object) {
		if (object == null) {
			log.warn("Received null byte array in generateIDNumber");
			return Mono.empty();
		}
		return Mono.fromCallable(() -> {
			PDDocument document = PDDocument.load(object);
			PDFTextStripper stripper = new PDFTextStripper();
			String text = stripper.getText(document);
			document.close();

			Pattern pattern = Pattern.compile("\\d{16}\\b");
			Matcher matcher = pattern.matcher(text);
			return matcher.find() ? matcher.group() : null;
		}).subscribeOn(Schedulers.boundedElastic())
		.onErrorResume(e -> {
			log.error("Error in generateIDNumber: {}", e.getMessage(), e);
			return Mono.empty();
		});
	}
}
