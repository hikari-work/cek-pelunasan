package org.cekpelunasan.configuration;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

/*
The bean for generating PDFs from HTML.
Using Selenium Webdriver to generate PDFs for complex page setup and easy to control.
 */

@Component
@RequiredArgsConstructor
public class PdfService {

	private static final Logger log = LoggerFactory.getLogger(PdfService.class);
	private final ResourceLoader resourceLoader;

	public String embedLocalResourceImages(String htmlContent) {
		/*
		Loading Images from local resources
		Specially for logo to shown in a PDF file
		 */
		try {
			// Parso html content
			Document doc = Jsoup.parse(htmlContent);
			for (Element img : doc.select("img")) {
				// Get image src
				String src = img.attr("src");

				// Check if image src is a local resource
				if (!src.startsWith("http") && !src.startsWith("data:")) {
					try {
						// Load image from local resources
						String resourcePath = "images/" + src;
						ClassPathResource resource = new ClassPathResource(resourcePath);

						if (resource.exists() && resource.isReadable()) {
							// Embed image into HTML
							try (InputStream is = resource.getInputStream()) {
								inputImages(img, src, is);
								log.debug("Successfully embedded image: {}", src);
							}
						} else {
							// Resource hasn't been found or not readable
							log.warn("Resource not found or not readable: {}", resourcePath);

							try {
								InputStream altStream = this.getClass().getClassLoader()
									.getResourceAsStream(resourcePath);
								if (altStream != null) {
									log.info("Found resource using alternative method: {}", resourcePath);
									try (InputStream is = altStream) {
										inputImages(img, src, is);
										log.debug("Successfully embedded image using alternative method: {}", src);
									}
								} else {
									log.warn("Resource not found with alternative method either: {}", resourcePath);
								}
							} catch (Exception altEx) {
								log.warn("Alternative resource loading failed: {}", altEx.getMessage());
							}
						}

					} catch (Exception e) {
						// Failed to load image
						log.error("Failed to embed image '{}': {}", src, e.getMessage(), e);
					}
				}
			}
			return doc.html();
		} catch (Exception e) {
			log.error("Error embedding local resource images: {}", e.getMessage(), e);
			return htmlContent;
		}
	}

	// Extract Images

	private void inputImages(Element img, String src, InputStream is) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[4096];
		int bytesRead;

		while ((bytesRead = is.read(buffer)) != -1) {
			out.write(buffer, 0, bytesRead);
		}

		byte[] imageBytes = out.toByteArray();

		String mimeType = detectMimeType(src);
		String base64Image = Base64.getEncoder().encodeToString(imageBytes);
		img.attr("src", "data:" + mimeType + ";base64," + base64Image);
	}

	private String detectMimeType(String filename) {
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
		return "image/jpeg";
	}

	@PostConstruct
	public void debugImageResources() {
		log.info("=== Debugging Image Resources ===");

		try {

			ClassPathResource logoResource = new ClassPathResource("images/logo.png");
			log.info("ClassPathResource - exists: {}, readable: {}",
				logoResource.exists(), logoResource.isReadable());

			if (logoResource.exists()) {
				log.info("ClassPathResource URI: {}", logoResource.getURI());
				log.info("ClassPathResource description: {}", logoResource.getDescription());
			}
		} catch (Exception e) {
			log.error("Error checking ClassPathResource: {}", e.getMessage());
		}

		try {

			InputStream stream = this.getClass().getClassLoader()
				.getResourceAsStream("images/logo.png");
			if (stream != null) {
				log.info("ClassLoader method found the resource");
				stream.close();
			} else {
				log.warn("ClassLoader method did not find the resource");
			}
		} catch (Exception e) {
			log.error("Error checking with ClassLoader: {}", e.getMessage());
		}

		try {

			org.springframework.core.io.Resource springResource =
				resourceLoader.getResource("classpath:images/logo.png");
			log.info("Spring ResourceLoader - exists: {}, readable: {}",
				springResource.exists(), springResource.isReadable());

			if (springResource.exists()) {
				log.info("Spring ResourceLoader URI: {}", springResource.getURI());
			}
		} catch (Exception e) {
			log.error("Error checking Spring ResourceLoader: {}", e.getMessage());
		}

		log.info("=== End Debug Image Resources ===");
	}
}
