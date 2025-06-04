package org.cekpelunasan.configuration;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Base64;

import static org.cekpelunasan.handler.command.CommandProcessor.log;

@Component
public class PdfService {

	private final ResourceLoader resourceLoader;

	@Autowired
	public PdfService(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}

	public String embedLocalResourceImages(String htmlContent) {
		try {
			Document doc = Jsoup.parse(htmlContent);

			// Find all images
			for (Element img : doc.select("img")) {
				String src = img.attr("src");

				// Skip URLs and data URLs
				if (!src.startsWith("http") && !src.startsWith("data:")) {
					try {
						// Try to load the resource
						String resourcePath = "classpath:static/images/" + src;
						org.springframework.core.io.Resource resource = resourceLoader.getResource(resourcePath);

						if (!resource.exists()) {
							// Try alternative path
							resourcePath = "classpath:images/" + src;
							resource = resourceLoader.getResource(resourcePath);
						}

						if (resource.exists()) {
							try (InputStream is = resource.getInputStream()) {
								// Read image bytes
								ByteArrayOutputStream out = new ByteArrayOutputStream();
								byte[] buffer = new byte[4096];
								int bytesRead;
								while ((bytesRead = is.read(buffer)) != -1) {
									out.write(buffer, 0, bytesRead);
								}

								byte[] imageBytes = out.toByteArray();

								// Determine MIME type
								String mimeType = resource.getFilename().endsWith(".png") ? "image/png" : "image/jpeg";

								// Convert to base64
								String base64Image = Base64.getEncoder().encodeToString(imageBytes);

								// Set data URL as src
								img.attr("src", "data:" + mimeType + ";base64," + base64Image);

								log.debug("Embedded image: {}", resourcePath);
							}
						} else {
							log.warn("Resource not found: {}", resourcePath);
						}
					} catch (Exception e) {
						log.warn("Failed to embed image {}: {}", src, e.getMessage());
					}
				}
			}

			return doc.html();
		} catch (Exception e) {
			log.warn("Error embedding local resource images: {}", e.getMessage());
			return htmlContent;
		}
	}
}
