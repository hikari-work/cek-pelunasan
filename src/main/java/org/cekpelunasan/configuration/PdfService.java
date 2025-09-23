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
import java.net.URLConnection;
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
						// Coba beberapa kemungkinan path
						String[] possiblePaths = {
							"classpath:images/" + src,           // Tanpa slash pertama
							"classpath:/images/" + src,          // Dengan slash pertama
							"classpath:static/images/" + src,    // Di folder static
							"classpath:/static/images/" + src    // Di folder static dengan slash
						};

						org.springframework.core.io.Resource resource = null;
						String foundPath = null;

						// Cari resource yang ada
						for (String path : possiblePaths) {
							org.springframework.core.io.Resource tempResource = resourceLoader.getResource(path);
							if (tempResource.exists()) {
								resource = tempResource;
								foundPath = path;
								break;
							}
						}

						if (resource != null && resource.exists()) {
							try (InputStream is = resource.getInputStream()) {
								ByteArrayOutputStream out = new ByteArrayOutputStream();
								byte[] buffer = new byte[4096];
								int bytesRead;
								log.info("Writing Images from path: {}", foundPath);

								while ((bytesRead = is.read(buffer)) != -1) {
									out.write(buffer, 0, bytesRead);
								}

								byte[] imageBytes = out.toByteArray();

								// Deteksi MIME type yang lebih akurat
								String mimeType = detectMimeType(src, resource);
								String base64Image = Base64.getEncoder().encodeToString(imageBytes);
								img.attr("src", "data:" + mimeType + ";base64," + base64Image);
								log.debug("Embedded image: {} from {}", src, foundPath);
							}
						} else {
							log.warn("Resource not found for any path. Tried paths for '{}': {}",
								src, String.join(", ", possiblePaths));

							// Debug: List semua resources di classpath:/images/
							try {
								org.springframework.core.io.Resource imagesDir = resourceLoader.getResource("classpath:images/");
								if (imagesDir.exists()) {
									log.debug("Images directory exists: {}", imagesDir.getURI());
								} else {
									log.debug("Images directory does not exist");
								}
							} catch (Exception debugEx) {
								log.debug("Error checking images directory: {}", debugEx.getMessage());
							}
						}

					} catch (Exception e) {
						log.warn("Failed to embed image '{}': {}", src, e.getMessage());
					}
				}
			}
			log.info("Embedded image generation complete");
			return doc.html();
		} catch (Exception e) {
			log.error("Error embedding local resource images: {}", e.getMessage(), e);
			return htmlContent;
		}
	}

	private String detectMimeType(String filename, org.springframework.core.io.Resource resource) {
		try {
			// Gunakan Spring's method untuk detect content type
			String contentType = URLConnection.guessContentTypeFromName(filename);
			if (contentType != null) {
				return contentType;
			}

			// Fallback berdasarkan ekstensi
			String lowerFilename = filename.toLowerCase();
			if (lowerFilename.endsWith(".png")) {
				return "image/png";
			} else if (lowerFilename.endsWith(".jpg") || lowerFilename.endsWith(".jpeg")) {
				return "image/jpeg";
			} else if (lowerFilename.endsWith(".gif")) {
				return "image/gif";
			} else if (lowerFilename.endsWith(".svg")) {
				return "image/svg+xml";
			} else if (lowerFilename.endsWith(".webp")) {
				return "image/webp";
			}

			// Default fallback
			return "image/jpeg";

		} catch (Exception e) {
			log.debug("Error detecting MIME type for {}: {}", filename, e.getMessage());
			return "image/jpeg";
		}
	}
}
