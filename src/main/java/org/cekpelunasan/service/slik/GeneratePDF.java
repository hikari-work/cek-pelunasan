package org.cekpelunasan.service.slik;


import lombok.RequiredArgsConstructor;
import org.cekpelunasan.configuration.PdfService;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
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
@RequiredArgsConstructor
public class GeneratePDF {

	private static final Logger log = LoggerFactory.getLogger(GeneratePDF.class);
	private final PdfService pdfService;


	public String sendBytesWithRestTemplate(byte[] fileBytes, String fileName, Boolean fasilitasAktif) {
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
		if (fasilitasAktif) {
			body.add("fasilitasAktif", "y");
		}

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
        	tempHtmlFile = Files.createTempFile("temp_content", ".html");

        	Files.writeString(tempHtmlFile, htmlContent);
        	log.info("HTML content written to temporary file");
			WebDriver driver = getWebDriver();

			try {
            	driver.get(tempHtmlFile.toUri().toString());
            	log.info("HTML loaded in Chrome Headless...");
            	log.info("Going To Generate PDT...");
            	Thread.sleep(1500);

				PageMargin margin = new PageMargin(0.5F, 0.5F, 0.5F, 0.5F);
				PageSize pageSize = new PageSize(29.7, 21);
            	PrintOptions printOptions = new PrintOptions();
            	printOptions.setBackground(true);
				printOptions.setPageSize(pageSize);
            	printOptions.setPageRanges("1-");
            	printOptions.setOrientation(PrintOptions.Orientation.LANDSCAPE);
				printOptions.setPageMargin(margin);
            
            	log.info("Generating PDF...");
            	Pdf pdf = ((PrintsPage) driver).print(printOptions);
            	byte[] pdfBytes = Base64.getDecoder().decode(pdf.getContent());
            	log.info("PDF generated successfully");
            	return pdfBytes;
        	} finally {
            	driver.quit();
            	log.info("WebDriver closed");
        	}
    	} catch (Exception e) {
        	log.error("PDF generation with Selenium failed Due To Exception");
        	return new byte[0];
    	} finally {
        	try {
            	if (tempHtmlFile != null) {
                	Files.deleteIfExists(tempHtmlFile);
            	}
        	} catch (IOException e) {
            	log.warn("Failed to delete temporary HTML file");
        	}
    	}
	}

	@NotNull
	private static WebDriver getWebDriver() {
		log.info("Set Up Webdriver....");
		ChromeOptions options = new ChromeOptions();
		options.addArguments("--headless=new");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		HashMap<String, Object> chromePrefs = new HashMap<>();
		chromePrefs.put("printing.default_destination_selection_rules", "{\"kind\": \"local\", \"namePattern\": \"Save as PDF\"}");
		chromePrefs.put("printing.print_preview_sticky_settings.appState",
			"{\"recentDestinations\":[{\"id\":\"Save as PDF\",\"origin\":\"local\"}]," +
			"\"selectedDestinationId\":\"Save as PDF\",\"version\":2," +
			"\"isLandscapeEnabled\":true,\"isHeaderFooterEnabled\":false}");
		options.setExperimentalOption("prefs", chromePrefs);
		return new ChromeDriver(options);
	}

	private String removeButtonsDiv(String htmlContent) {
		log.info("Processing HTML content...");
		try {
			Document doc = Jsoup.parse(htmlContent);
			doc.select("div.text-right").forEach(element -> {
				if (!element.select("button#print").isEmpty()) {
					element.remove();
				}
			});

			Elements divBlocks = doc.select("div[style*=grid-template-columns: 80px 80px 80px]");
			for (Element block : divBlocks) {
				Element clonedBlock = block.clone();
				block.remove();

				Element wrapper = new Element("div");
				wrapper.attr("style", "display: flex; justify-content: flex-end; margin-top: 20px;");
				wrapper.appendChild(clonedBlock);
				doc.body().appendChild(wrapper);
			}

			return doc.html();
		} catch (Exception e) {
			log.warn("Error processing HTML: {}", e.getMessage());
			return htmlContent;
		}
	}


}