package org.cekpelunasan.configuration;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Base64;
import java.util.Objects;


@Component
@RequiredArgsConstructor
public class PdfService {

	private static final Logger log = LoggerFactory.getLogger(PdfService.class);
	private final ResourceLoader resourceLoader;

	public String embedLocalResourceImages(String htmlContent) {
		log.info("Generating Image For PDF...");
		try {
			Document doc = Jsoup.parse(htmlContent);
			for (Element img : doc.select("img")) {
				String src = img.attr("src");

				if (!src.startsWith("http") && !src.startsWith("data:")) {
					try {
						String resourcePath = "classpath:static/images/" + src;
						org.springframework.core.io.Resource resource = resourceLoader.getResource(resourcePath);

						if (!resource.exists()) {
							resourcePath = "classpath:images/" + src;
							resource = resourceLoader.getResource(resourcePath);
						}

						if (resource.exists()) {
							try (InputStream is = resource.getInputStream()) {
								ByteArrayOutputStream out = new ByteArrayOutputStream();
								byte[] buffer = new byte[4096];
								int bytesRead;
								log.info("Writing Images...");
								while ((bytesRead = is.read(buffer)) != -1) {
									out.write(buffer, 0, bytesRead);
								}

								byte[] imageBytes = out.toByteArray();
								String mimeType = Objects.requireNonNull(resource.getFilename()).endsWith(".png") ? "image/png" : "image/jpeg";
								String base64Image = Base64.getEncoder().encodeToString(imageBytes);
								img.attr("src", "data:" + mimeType + ";base64," + base64Image);
								log.debug("Embedded image: {}", resourcePath);
							}

						} else {
							log.warn("Resource not found: {}", resourcePath);
						}

					} catch (Exception e) {
						log.warn("Failed to embed image {}", src);
					}
				}
			}
			log.info("Embedded image generation complete");
			return doc.html();
		} catch (Exception e) {
			log.warn("Error embedding local resource images: {}", e.getMessage());
			return htmlContent;
		}
	}
}
