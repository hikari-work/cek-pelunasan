package org.cekpelunasan.service.s3;

import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.core.service.slik.GeneratePdfFiles;
import org.cekpelunasan.configuration.S3ClientConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.FileOutputStream;

@Slf4j
@SpringBootTest
public class GenerateTest {

	@Autowired
	private GeneratePdfFiles generatePdfFiles;

	@Autowired
	private S3ClientConfiguration S3configuration;

	@Test
	void generate() {
		byte[] file = S3configuration.getFile("KTP_3175040206810003.txt").block();
		Assertions.assertNotNull(file);
		byte[] pdf = generatePdfFiles.generatePdf(file, true).block();
		Assertions.assertNotNull(pdf);
	}

	@Test
	void generateByte() {
		long start = System.currentTimeMillis();
		byte[] file = S3configuration.getFile("KTP_3175040206810003.txt").block();
		log.info("Get Object In {} ms", System.currentTimeMillis() - start);
		Assertions.assertNotNull(file);

		byte[] bytes = generatePdfFiles.generatePdf(file, true).block();
		log.info("Generate PDF In {} ms", System.currentTimeMillis() - start);
		Assertions.assertNotNull(bytes);
		try (FileOutputStream outputStream = new FileOutputStream("KTP_3175040206810003.pdf")) {
			outputStream.write(bytes);
		} catch (Exception e) {
			e.printStackTrace();
		}
		log.info("Time is {} ms", System.currentTimeMillis() - start);
	}
}
