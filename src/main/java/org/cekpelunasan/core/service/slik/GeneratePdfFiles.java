package org.cekpelunasan.core.service.slik;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.impl.TargetClosedError;
import com.microsoft.playwright.options.Margin;
import org.cekpelunasan.configuration.PlaywrightBrowserPool;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class GeneratePdfFiles {

	private static final Logger logger = LoggerFactory.getLogger(GeneratePdfFiles.class);
	private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)";

	@Value("${pdf.endpoint.url:https://kredit.suryayudha.id/ideb/generate.php}")
	private String pdfEndpointUrl;

	@Value("${pdf.logo.url:https://kredit.suryayudha.id/ideb/logo.png}")
	private String logoUrl;

	private final WebClient webClient;
	private final PlaywrightBrowserPool browserPool;

	public GeneratePdfFiles(WebClient whatsappWebClient, PlaywrightBrowserPool browserPool) {
		this.webClient = whatsappWebClient;
		this.browserPool = browserPool;
	}

	/**
	 * Full reactive pipeline: fetch HTML → parse/manipulate → render PDF.
	 */
	public Mono<byte[]> generatePdf(byte[] pdfBytes, boolean fasilitasAktif) {
		if (pdfBytes == null || pdfBytes.length == 0) {
			logger.warn("PDF bytes is null or empty");
			return Mono.empty();
		}
		return fetchHtmlFromEndpoint(pdfBytes, fasilitasAktif)
			.flatMap(this::parseAndManipulateHtml)
			.map(Document::outerHtml)
			.flatMap(this::renderPdfWithPlaywright);
	}

	private Mono<String> fetchHtmlFromEndpoint(byte[] body, boolean fasilitasAktif) {
		MultipartBodyBuilder builder = new MultipartBodyBuilder();
		builder.part("fileToUpload", body)
			.filename("ideb.txt")
			.contentType(MediaType.TEXT_PLAIN);
		builder.part("fasilitasAktif", fasilitasAktif ? "y" : "n");

		return webClient.post()
			.uri(pdfEndpointUrl)
			.header("User-Agent", USER_AGENT)
			.body(BodyInserters.fromMultipartData(builder.build()))
			.retrieve()
			.bodyToMono(String.class)
			.onErrorResume(e -> {
				logger.error("Error fetching HTML from PDF endpoint: {}", e.getMessage());
				return Mono.empty();
			});
	}

	private Mono<Document> parseAndManipulateHtml(String htmlContent) {
		if (htmlContent == null || htmlContent.isBlank()) {
			logger.warn("HTML content is null or blank");
			return Mono.empty();
		}
		return Mono.fromCallable(() -> {
			Document document = Jsoup.parse(htmlContent);
			removeScriptTag(document);
			insertingImages(document);
			removePrintButtons(document);
			fixSignatureGrid(document);
			return document;
		}).subscribeOn(Schedulers.boundedElastic());
	}

	private Mono<byte[]> renderPdfWithPlaywright(String html) {
		return Mono.<byte[]>fromCallable(() -> {
			Browser browser = browserPool.acquire();
			boolean crashed = false;
			try (Page page = browser.newPage()) {
				page.setContent(html);
				return page.pdf(new Page.PdfOptions()
					.setFormat("A4")
					.setLandscape(true)
					.setMargin(new Margin()
						.setTop("15mm")
						.setBottom("15mm")
						.setLeft("15mm")
						.setRight("15mm")));
			} catch (TargetClosedError e) {
				crashed = true;
				throw e;
			} finally {
				browserPool.release(crashed ? null : browser);
			}
		}).subscribeOn(Schedulers.boundedElastic());
	}

	private void removeScriptTag(Document doc) {
		doc.getElementsByTag("script").remove();
	}

	private void insertingImages(Document document) {
		Element image = document.selectFirst("img.right-image");
		if (image == null) {
			return;
		}
		image.attr("src", logoUrl);
		image.removeAttr("style");
		image.attr("style", "width: 160px;");

		Element headerTable = new Element("table");
		headerTable.attr("style", "width: 100%; border: none; margin-bottom: 20px; border-collapse: collapse;");

		Element row = new Element("tr");
		headerTable.appendChild(row);

		Element tdLeft = new Element("td");
		tdLeft.attr("style", "border: none; vertical-align: middle; text-align: left;");
		document.select("h3").forEach(h3 -> {
			h3.attr("style", "margin: 2px 0; font-family: sans-serif;");
			tdLeft.appendChild(h3);
		});
		row.appendChild(tdLeft);

		Element tdRight = new Element("td");
		tdRight.attr("style",
			"border: none; vertical-align: middle; text-align: right; width: 1%; white-space: nowrap;");
		tdRight.appendChild(image);
		row.appendChild(tdRight);

		document.body().prependChild(headerTable);
		logger.debug("Header layout converted to Table for PDF stability");
	}

	private void removePrintButtons(Document doc) {
		doc.select("div.text-right").forEach(element -> {
			if (!element.select("button#print").isEmpty()) {
				element.remove();
			}
		});
	}

	private void fixSignatureGrid(Document doc) {
		Element gridDiv = doc.selectFirst("div[style*='display: grid']");

		if (gridDiv != null) {
			List<Element> children = new ArrayList<>(gridDiv.children());

			Element table = new Element("table");
			table.attr("style",
				"width: 300px; border-collapse: collapse; font-family: sans-serif; font-size: 12px; border: 0.5px solid blue; margin-top: 50px; page-break-inside: avoid;");

			Element tbody = new Element("tbody");
			table.appendChild(tbody);

			Element row1 = new Element("tr");
			for (int i = 0; i < 3 && i < children.size(); i++) {
				Element originalDiv = children.get(i);
				Element td = new Element("td");
				td.attr("style",
					"border: 0.5px solid blue; font-weight: bold; color: blue; text-align: center; padding: 4px; width: 100px;");
				td.text(originalDiv.text());
				row1.appendChild(td);
			}
			tbody.appendChild(row1);

			Element row2 = new Element("tr");
			for (int i = 3; i < 6 && i < children.size(); i++) {
				Element td = new Element("td");
				td.attr("style", "border: 0.5px solid blue; height: 40px;");
				row2.appendChild(td);
			}
			tbody.appendChild(row2);

			Element container = doc.selectFirst("div.printableArea");
			Objects.requireNonNullElseGet(container, doc::body).appendChild(table);

			gridDiv.remove();

			Element parentFlex = doc.selectFirst("div[style*='display: flex']");
			if (parentFlex != null && parentFlex.childrenSize() <= 1) {
				parentFlex.attr("style", "font-family: sans-serif;");
			}
		}
	}
}
