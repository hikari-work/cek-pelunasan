package org.cekpelunasan.core.service.slik;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class PDFReader {

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
