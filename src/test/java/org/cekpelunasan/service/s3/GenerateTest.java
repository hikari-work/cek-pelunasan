package org.cekpelunasan.service.s3;

import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.service.slik.GeneratePdfFiles;
import org.cekpelunasan.service.slik.S3ClientConfiguration;
import org.jsoup.nodes.Document;
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
		byte[] file = S3configuration.getFile("KTP_3175040206810003.txt");
		String s = generatePdfFiles.generateHtmlContent(file);
		Document document = generatePdfFiles.parsingHtmlContentAndManipulatePages(s);
		Assertions.assertNotNull(document);
		System.out.println(document.html());
	}
	@Test
	void generateByte() {
		long start = System.currentTimeMillis();
		byte[] file = S3configuration.getFile("KTP_3175040206810003.txt");
		log.info("Get Object In {} ms", System.currentTimeMillis() - start);
		String s = generatePdfFiles.generateHtmlContent(file);
		log.info("Generate HTML In {} ms", System.currentTimeMillis() - start);
		Document document = generatePdfFiles.parsingHtmlContentAndManipulatePages(s);
		log.info("Parsing HTML In {} ms", System.currentTimeMillis() - start);
		log.info(document.html());
		byte[] bytes = generatePdfFiles.generatePdfBytes(document);
		log.info("Generate PDF In {} ms", System.currentTimeMillis() - start);
		Assertions.assertNotNull(bytes);
		try (FileOutputStream outputStream = new FileOutputStream("KTP_3175040206810003.pdf")){
			outputStream.write(bytes);
		}catch (Exception e){
			e.printStackTrace();
		}

		log.info("Time is {} ms", System.currentTimeMillis() - start);
	}
}
