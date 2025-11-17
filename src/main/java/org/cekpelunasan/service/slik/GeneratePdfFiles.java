package org.cekpelunasan.service.slik;

import okhttp3.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class GeneratePdfFiles {

	private final OkHttpClient okHttpClient;

	public GeneratePdfFiles(OkHttpClient okHttpClient) {
		this.okHttpClient = okHttpClient;
	}



	public String generateHtmlContent(byte[] pdfBytes) {
		if (pdfBytes == null || pdfBytes.length == 0) {
			return null;
		}
		return responseFromEndpoint(pdfBytes);
	}

	public String responseFromEndpoint(byte[] body) {
		RequestBody requestBody = new MultipartBody.Builder()
			.setType(MultipartBody.FORM)
			.addFormDataPart("fileToUpload", "ideb.txt",
				RequestBody.create(body, MediaType.parse("text/plain")))
			.build();
		Request request = new okhttp3.Request.Builder()
			.url("https://kredit.suryayudha.id/ideb/generate.php")
			.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
			.post(requestBody)
			.build();
		try (Response response = okHttpClient.newCall(request).execute()){
			if (!response.isSuccessful()) {
				return null;
			}
			return response.body().string();
		} catch (Exception e) {
			return null;
		}
	}

	private Document parsingHtmlContent(String htmlContent) {
		return Jsoup.parse(htmlContent);
	}
	private Document removeScriptTag(Document doc) {
		doc.getElementsByTag("script").remove();
		return doc;
	}
	private Document moveTableToLast(Document document) {
		Element table = document.selectFirst("div[style*='display: grid']");
		if (table == null) {
			return document;
		}
		Element parent = table.parent();
		if (parent == null) {
			return document;
		}
		table.remove();
		parent.appendChild(table);
		return document;
	}
	private Document insertingImages(Document document) {
		Element image = document.selectFirst("img.right-image");
		if (image == null) {
			return document;
		}
		image.attr("src", "https://kredit.suryayudha.id/ideb/logo.png");
		return document;
	}


}
