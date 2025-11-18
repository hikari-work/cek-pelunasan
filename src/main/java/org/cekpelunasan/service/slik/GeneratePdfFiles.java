package org.cekpelunasan.service.slik;

import okhttp3.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Objects;

@Component
public class GeneratePdfFiles {

	private static final Logger logger = LoggerFactory.getLogger(GeneratePdfFiles.class);
	private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)";

	@Value("${pdf.endpoint.url:https://kredit.suryayudha.id/ideb/generate.php}")
	private String pdfEndpointUrl;

	@Value("${pdf.logo.url:https://kredit.suryayudha.id/ideb/logo.png}")
	private String logoUrl;

	private final OkHttpClient okHttpClient;

	public GeneratePdfFiles(OkHttpClient okHttpClient) {
		this.okHttpClient = Objects.requireNonNull(okHttpClient, "OkHttpClient cannot be null");
	}

	public String generateHtmlContent(byte[] pdfBytes) {
		if (pdfBytes == null || pdfBytes.length == 0) {
			logger.warn("PDF bytes is null or empty");
			return null;
		}
		return responseFromEndpoint(pdfBytes);
	}

	private String responseFromEndpoint(byte[] body) {
		RequestBody requestBody = new MultipartBody.Builder()
			.setType(MultipartBody.FORM)
			.addFormDataPart("fileToUpload", "ideb.txt",
				RequestBody.create(body, MediaType.parse("text/plain")))
			.build();

		Request request = new okhttp3.Request.Builder()
			.url(pdfEndpointUrl)
			.header("User-Agent", USER_AGENT)
			.post(requestBody)
			.build();

		try (Response response = okHttpClient.newCall(request).execute()) {
			if (!response.isSuccessful()) {
				logger.error("Failed to get response from endpoint. Status code: {}", response.code());
				return null;
			}

			ResponseBody responseBody = response.body();

			return responseBody.string();
		} catch (IOException e) {
			logger.error("Error calling PDF endpoint", e);
			return null;
		} catch (Exception e) {
			logger.error("Unexpected error in responseFromEndpoint", e);
			return null;
		}
	}

	public Document parsingHtmlContentAndManipulatePages(String htmlContent) {
		if (htmlContent == null || htmlContent.isBlank()) {
			logger.warn("HTML content is null or blank");
			return null;
		}

		Document document = Jsoup.parse(htmlContent);
		removeScriptTag(document);
		moveTableToLast(document);
		insertingImages(document);
		return document;
	}

	private void removeScriptTag(Document doc) {
		doc.getElementsByTag("script").remove();
		doc.getElementsByTag("style").remove();
	}

	private void moveTableToLast(Document document) {
		Elements table = document.select("div[style*='grid-template-columns: 80px 80px 80px']");
		for (Element block : table) {
			Element clonedBlock = block.clone();
			block.remove();

			Element wrapper = createFlexWrapper(clonedBlock);
			document.body().appendChild(wrapper);
		}
	}

	private void insertingImages(Document document) {
		Element image = document.selectFirst("img.right-image");
		if (image == null) {
			logger.debug("Image element not found");
			return;
		}

		image.attr("src", logoUrl);
		logger.debug("Image source updated");
	}
	private Element createFlexWrapper(Element block) {
		Element wrapper = new Element("div");
		wrapper.attr("style", "display: flex; justify-content: flex-end; margin-top: 20px;");
		wrapper.appendChild(block);
		return wrapper;
	}
}