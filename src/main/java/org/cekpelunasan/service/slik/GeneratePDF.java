package org.cekpelunasan.service.slik;


import org.cekpelunasan.configuration.PdfService;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.openqa.selenium.Pdf;
import org.openqa.selenium.PrintsPage;
import org.openqa.selenium.print.PageMargin;
import org.openqa.selenium.print.PageSize;
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

import java.nio.file.Files;
import java.io.IOException;

import java.nio.file.Path;


import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.print.PrintOptions;

import java.util.Base64;
import java.util.HashMap;


@Component
public class GeneratePDF {

	private static final Logger log = LoggerFactory.getLogger(GeneratePDF.class);
	private final PdfService pdfService;

	public GeneratePDF(PdfService pdfService) {
		this.pdfService = pdfService;
	}

	public String sendBytesWithRestTemplate(byte[] fileBytes, String fileName) {
    	String url = "https://kredit.suryayudha.id/ideb/generate.php";
    	RestTemplate restTemplate = new RestTemplate();

    	HttpHeaders headers = new HttpHeaders();
    	headers.setContentType(MediaType.MULTIPART_FORM_DATA);


    	MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

    	ByteArrayResource resource = new ByteArrayResource(fileBytes) {
        	@Override
        	public String getFilename() {
            	return fileName;
        	}
    	};

    	body.add("fileToUpload", resource);

    	HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

    	ResponseEntity<String> response = restTemplate.exchange(
        	url,
        	HttpMethod.POST,
        	requestEntity,
        	String.class
    	);
		if (response.getBody() != null) {
			return response.getBody();
		}
		return null;
	}

	public byte[] convertHtmlToPdf(String htmlContent) {
    	log.info("Converting HTML to PDF using Selenium headless browser...");
    	htmlContent = removeButtonsDiv(htmlContent);
		htmlContent = pdfService.embedLocalResourceImages(htmlContent);
    	Path tempHtmlFile = null;
    	try {
        	// Create temporary HTML file
        	tempHtmlFile = Files.createTempFile("temp_content", ".html");

        	Files.writeString(tempHtmlFile, htmlContent);
        	log.info("HTML content written to temporary file: {}", tempHtmlFile);
			WebDriver driver = getWebDriver();

			try {
            	driver.get(tempHtmlFile.toUri().toString());
            	log.info("HTML loaded in headless browser");
            	Thread.sleep(1500);

				PageMargin margin = new PageMargin(0.5F, 0.5F, 0.5F, 0.5F);
				PageSize pageSize = new PageSize(29.7, 21);
            	PrintOptions printOptions = new PrintOptions();
            	printOptions.setBackground(true);
				printOptions.setPageSize(pageSize);
            	printOptions.setPageRanges("1-");
            	printOptions.setOrientation(PrintOptions.Orientation.LANDSCAPE);
				printOptions.setPageMargin(margin);
            
            	// Print to PDF
            	Pdf pdf = ((PrintsPage) driver).print(printOptions);
            	byte[] pdfBytes = Base64.getDecoder().decode(pdf.getContent());
            
            	log.info("PDF generated successfully");
            
            	return pdfBytes;
        	} finally {
            	// Always close the driver
            	driver.quit();
            	log.info("WebDriver closed");
        	}
    	} catch (Exception e) {
        	log.error("PDF generation with Selenium failed", e);
        	e.printStackTrace();
        	return new byte[0];
    	} finally {
        	try {
            	if (tempHtmlFile != null) {
                	Files.deleteIfExists(tempHtmlFile);
            	}
        	} catch (IOException e) {
            	log.warn("Failed to delete temporary HTML file", e);
        	}
    	}
	}

	@NotNull
	private static WebDriver getWebDriver() {
		ChromeOptions options = new ChromeOptions();
		options.addArguments("--headless=new");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		// Add print arguments for landscape orientation
		HashMap<String, Object> chromePrefs = new HashMap<>();
		chromePrefs.put("printing.default_destination_selection_rules", "{\"kind\": \"local\", \"namePattern\": \"Save as PDF\"}");
		chromePrefs.put("printing.print_preview_sticky_settings.appState",
			"{\"recentDestinations\":[{\"id\":\"Save as PDF\",\"origin\":\"local\"}]," +
			"\"selectedDestinationId\":\"Save as PDF\",\"version\":2," +
			"\"isLandscapeEnabled\":true,\"isHeaderFooterEnabled\":false}");
		options.setExperimentalOption("prefs", chromePrefs);
		WebDriver driver = new ChromeDriver(options);
		return driver;
	}

	private String removeButtonsDiv(String htmlContent) {
		try {
			// Parse the HTML document
			Document doc = Jsoup.parse(htmlContent);

			// Find and remove the div with the print and back buttons
			// This selector targets a div with class "text-right" containing a button with id "print"
			doc.select("div.text-right").forEach(element -> {
				if (!element.select("button#print").isEmpty()) {
					element.remove();
				}
			});

			return doc.html();
		} catch (Exception e) {
			log.warn("Error removing print button div: {}", e.getMessage());
			return htmlContent;
		}
	}

}