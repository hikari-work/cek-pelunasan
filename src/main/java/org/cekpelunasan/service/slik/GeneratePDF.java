package org.cekpelunasan.service.slik;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.configuration.PdfService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.Pdf;
import org.openqa.selenium.PrintsPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.print.PrintOptions;

@Component
@RequiredArgsConstructor
public class GeneratePDF {

	private static final Logger log = LoggerFactory.getLogger(GeneratePDF.class);
	private static final String PDF_GENERATION_URL = "https://kredit.suryayudha.id/ideb/generate.php";
	private static final long PDF_GENERATION_DELAY_MS = 1500;

	private final PdfService pdfService;
	private final WebDriver driver;
	private final PrintOptions printOptions;

	/**
	 * Send file bytes to remote server using RestTemplate
	 */
	public String sendBytesWithRestTemplate(byte[] fileBytes, String fileName, Boolean fasilitasAktif) {
		try {
			HttpEntity<MultiValueMap<String, Object>> requestEntity = buildMultipartRequest(fileBytes, fileName, fasilitasAktif);
			ResponseEntity<String> response = executePostRequest(requestEntity);
			return extractResponseBody(response);
		} catch (Exception e) {
			log.error("Error sending bytes to remote server", e);
			return null;
		}
	}

	/**
	 * Convert HTML content to PDF using Selenium headless browser
	 */
	public byte[] convertHtmlToPdf(String htmlContent) {
		log.info("Converting HTML to PDF using Selenium headless browser...");
		Path tempHtmlFile = null;

		try {
			htmlContent = preprocessHtmlContent(htmlContent);
			tempHtmlFile = createTempHtmlFile(htmlContent);
			return generatePdfFromHtmlFile(tempHtmlFile);
		} catch (Exception e) {
			log.error("PDF generation with Selenium failed", e);
			return new byte[0];
		} finally {
			deleteTempFile(tempHtmlFile);
		}
	}

	// ===== RestTemplate Helper Methods =====

	/**
	 * Build multipart request with file and parameters
	 */
	private HttpEntity<MultiValueMap<String, Object>> buildMultipartRequest(byte[] fileBytes, String fileName, Boolean fasilitasAktif) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);

		MultiValueMap<String, Object> body = buildRequestBody(fileBytes, fileName, fasilitasAktif);
		return new HttpEntity<>(body, headers);
	}

	/**
	 * Build request body with file and optional parameters
	 */
	private MultiValueMap<String, Object> buildRequestBody(byte[] fileBytes, String fileName, Boolean fasilitasAktif) {
		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

		ByteArrayResource resource = new ByteArrayResource(fileBytes) {
			@Override
			public String getFilename() {
				return fileName;
			}
		};

		body.add("fileToUpload", resource);
		if (fasilitasAktif) {
			body.add("fasilitasAktif", "y");
		}

		return body;
	}

	/**
	 * Execute POST request to remote server
	 */
	private ResponseEntity<String> executePostRequest(HttpEntity<MultiValueMap<String, Object>> requestEntity) {
		RestTemplate restTemplate = new RestTemplate();
		return restTemplate.exchange(
			PDF_GENERATION_URL,
			HttpMethod.POST,
			requestEntity,
			String.class
		);
	}

	/**
	 * Extract response body from ResponseEntity
	 */
	private String extractResponseBody(ResponseEntity<String> response) {
		return response.getBody() != null ? response.getBody() : null;
	}

	// ===== PDF Conversion Helper Methods =====

	/**
	 * Preprocess HTML content before PDF generation
	 */
	private String preprocessHtmlContent(String htmlContent) {
		htmlContent = removeButtonsDiv(htmlContent);
		htmlContent = pdfService.embedLocalResourceImages(htmlContent);
		return htmlContent;
	}

	/**
	 * Create temporary HTML file from content
	 */
	private Path createTempHtmlFile(String htmlContent) throws IOException {
		Path tempHtmlFile = Files.createTempFile("temp_content", ".html");
		Files.writeString(tempHtmlFile, htmlContent);
		log.info("HTML content written to temporary file: {}", tempHtmlFile);
		return tempHtmlFile;
	}

	/**
	 * Generate PDF from temporary HTML file
	 */
	private byte[] generatePdfFromHtmlFile(Path tempHtmlFile) throws InterruptedException {
		loadHtmlInBrowser(tempHtmlFile);
		waitForPageRender();
		return capturePdfContent();
	}

	/**
	 * Load HTML file in Selenium WebDriver
	 */
	private void loadHtmlInBrowser(Path tempHtmlFile) {
		driver.get(tempHtmlFile.toUri().toString());
		log.info("HTML loaded in Chrome Headless");
	}

	/**
	 * Wait for page to fully render before capturing PDF
	 */
	private void waitForPageRender() throws InterruptedException {
		log.info("Waiting for page to render...");
		Thread.sleep(PDF_GENERATION_DELAY_MS);
	}

	/**
	 * Capture PDF content from browser
	 */
	private byte[] capturePdfContent() {
		log.info("Generating PDF from rendered page...");
		Pdf pdf = ((PrintsPage) driver).print(printOptions);
		byte[] pdfBytes = Base64.getDecoder().decode(pdf.getContent());
		log.info("PDF generated successfully");
		return pdfBytes;
	}

	/**
	 * Delete temporary file if it exists
	 */
	private void deleteTempFile(Path tempHtmlFile) {
		try {
			if (tempHtmlFile != null) {
				Files.deleteIfExists(tempHtmlFile);
				log.info("Temporary HTML file deleted");
			}
		} catch (IOException e) {
			log.warn("Failed to delete temporary HTML file: {}", e.getMessage());
		}
	}

	// ===== HTML Processing Methods =====

	/**
	 * Remove print buttons and reorganize grid layout elements
	 */
	private String removeButtonsDiv(String htmlContent) {
		log.info("Processing HTML content...");
		try {
			Document doc = Jsoup.parse(htmlContent);
			removePrintButtons(doc);
			reorganizeGridLayout(doc);
			return doc.html();
		} catch (Exception e) {
			log.warn("Error processing HTML: {}", e.getMessage());
			return htmlContent;
		}
	}

	/**
	 * Remove print button containers from document
	 */
	private void removePrintButtons(Document doc) {
		doc.select("div.text-right").forEach(element -> {
			if (!element.select("button#print").isEmpty()) {
				log.debug("Removing print button container");
				element.remove();
			}
		});
	}

	/**
	 * Reorganize grid layout elements to flex layout
	 */
	private void reorganizeGridLayout(Document doc) {
		Elements divBlocks = doc.select("div[style*=grid-template-columns: 80px 80px 80px]");

		for (Element block : divBlocks) {
			Element clonedBlock = block.clone();
			block.remove();

			Element wrapper = createFlexWrapper(clonedBlock);
			doc.body().appendChild(wrapper);
		}
	}

	/**
	 * Create flex wrapper element for block
	 */
	private Element createFlexWrapper(Element block) {
		Element wrapper = new Element("div");
		wrapper.attr("style", "display: flex; justify-content: flex-end; margin-top: 20px;");
		wrapper.appendChild(block);
		return wrapper;
	}
}