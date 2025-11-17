package org.cekpelunasan.service.slik;

import okhttp3.*;
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


}
