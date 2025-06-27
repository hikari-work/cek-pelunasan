package org.cekpelunasan.service.slik;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class PDFReader {

	public String generateIDNumber(byte[] object) {
		if (object == null) {
			log.warn("Received null byte array in generateIDNumber");
			return null;
		}

		try {
			PDDocument document = PDDocument.load(object);
			PDFTextStripper stripper = new PDFTextStripper();
			String text = stripper.getText(document);
			document.close();

			Pattern pattern = Pattern.compile("\\d{16}\\b");
			Matcher matcher = pattern.matcher(text);
			if (matcher.find()) {
				return matcher.group();
			} else {
				return null;
			}

		} catch (Exception e) {
			log.error("Error in generateIDNumber: {}", e.getMessage(), e);
			return null;
		}
	}
}