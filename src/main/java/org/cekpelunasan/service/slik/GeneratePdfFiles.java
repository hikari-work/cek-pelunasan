package org.cekpelunasan.service.slik;


import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import okhttp3.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
		logger.info("Getting Content");
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
		logger.info("Generated request");
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
		insertingImages(document);
		removePrintButtons(document);
		fixSignatureGrid(document);
		return document;
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

		Element headerTable= new Element("table");
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
		tdRight.attr("style", "border: none; vertical-align: middle; text-align: right; width: 1%; white-space: nowrap;");
		tdRight.appendChild(image);
		row.appendChild(tdRight);


		document.body().prependChild(headerTable);

		logger.debug("Header layout converted to Table for PDF stability");
	}


	public byte[] generatePdfBytes(Document htmlContent) {
		if (htmlContent == null) {
			return null;
		}
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream()){
			PdfWriter writer = new PdfWriter(baos);
			PdfDocument document = new PdfDocument(writer);
			PageSize pdfSize = new PageSize(842, 595);
			String htmlWithCss = "<html><head><style>@page { size: A4 landscape; margin: 15mm; }</style></head><body>"
				+ htmlContent.html() + "</body></html>";
			document.setDefaultPageSize(pdfSize);
			HtmlConverter.convertToPdf(htmlWithCss, writer);
			return baos.toByteArray();
		} catch (IOException e) {
			logger.error("Error generating PDF bytes", e);
			return null;
		} catch (Exception e) {
			logger.error("Unexpected error in generatePdfBytes", e);
			return null;
		}
	}
	private void removePrintButtons(Document doc) {
		doc.select("div.text-right").forEach(element -> {
			if (!element.select("button#print").isEmpty()) {
				element.remove();
			}
		});
	}

	private void fixSignatureGrid(Document doc) {
		Element grid = doc.selectFirst("div[style*='display: grid']");

		if (grid == null) {
			return;
		}
		List<Element> children = new ArrayList<>(grid.children());
		Element table = new Element("table");
		table.attr("style", "width: 240px; border-collapse: collapse; font-family: sans-serif; font-size: 12px; border: 0.5px solid blue; margin-left: auto;");

		Element tbody = new Element("tbody");
		table.appendChild(tbody);
		Element row1 = new Element("tr");
		for (int i = 0; i < 3 && i < children.size(); i++) {
			Element originalDiv = children.get(i);
			Element td = new Element("td");
			td.attr("style", "border: 0.5px solid blue; font-weight: bold; color: blue; text-align: center; padding: 4px; width: 80px;");
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

		grid.replaceWith(table);

		Element flexParent = doc.selectFirst("div[style*='display: flex']");
		if (flexParent != null) {
			convertFlexParentToTable(flexParent);
		}


	}

	private void convertFlexParentToTable(Element flexDiv) {
		if (flexDiv.childrenSize() < 2) {
			return;
		}

		Element leftChild = flexDiv.child(0);
		Element rightChild = flexDiv.child(1);

		Element table = new Element("table");
		table.attr("style", "width: 100%; border: none;");
		Element row = new Element("tr");
		table.appendChild(row);

		Element tdLeft = new Element("td");
		tdLeft.attr("style", "vertical-align: top; border: none;");
		tdLeft.appendChild(leftChild);
		row.appendChild(tdLeft);


		Element tdRight = new Element("td");
		tdRight.attr("style", "vertical-align: top; text-align: right; border: none;");
		tdRight.appendChild(rightChild);
		row.appendChild(tdRight);

		flexDiv.replaceWith(table);
	}
}