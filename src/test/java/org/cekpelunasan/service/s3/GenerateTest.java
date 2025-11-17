package org.cekpelunasan.service.s3;

import org.cekpelunasan.service.slik.GeneratePdfFiles;
import org.cekpelunasan.service.slik.S3ClientConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class GenerateTest {


	@Autowired
	private GeneratePdfFiles generatePdfFiles;

	@Autowired
	private S3ClientConfiguration S3configuration;

	@Test
	void generate() {
		byte[] file = S3configuration.getFile("KTP_3175040206810003.txt");
		String s = generatePdfFiles.generateHtmlContent(file);
		System.out.println(s);
		Assertions.assertNotNull(s);
	}
}
