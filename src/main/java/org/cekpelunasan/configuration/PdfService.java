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
						// Untuk Spring Boot JAR, gunakan path tanpa leading slash
						String resourcePath = "images/" + src;

						// Gunakan ClassPathResource untuk Spring Boot compatibility
						ClassPathResource resource = new ClassPathResource(resourcePath);

						log.debug("Trying to load resource: {}", resourcePath);
						log.debug("Resource exists: {}", resource.exists());
						log.debug("Resource is readable: {}", resource.isReadable());

						if (resource.exists() && resource.isReadable()) {
							try (InputStream is = resource.getInputStream()) {
								ByteArrayOutputStream out = new ByteArrayOutputStream();
								byte[] buffer = new byte[4096];
								int bytesRead;
								log.info("Writing Image: {} from {}", src, resourcePath);

								while ((bytesRead = is.read(buffer)) != -1) {
									out.write(buffer, 0, bytesRead);
								}

								byte[] imageBytes = out.toByteArray();
								log.debug("Image size: {} bytes", imageBytes.length);

								// Deteksi MIME type
								String mimeType = detectMimeType(src);
								String base64Image = Base64.getEncoder().encodeToString(imageBytes);
								img.attr("src", "data:" + mimeType + ";base64," + base64Image);
								log.debug("Successfully embedded image: {}", src);
							}
						} else {
							log.warn("Resource not found or not readable: {}", resourcePath);

							// Debug additional info
							try {
								log.debug("Resource URI: {}", resource.getURI());
								log.debug("Resource description: {}", resource.getDescription());
							} catch (Exception debugEx) {
								log.debug("Could not get resource details: {}", debugEx.getMessage());
							}

							// Alternatif: coba dengan ResourceUtils
							try {
								InputStream altStream = this.getClass().getClassLoader()
									.getResourceAsStream(resourcePath);
								if (altStream != null) {
									log.info("Found resource using alternative method: {}", resourcePath);
									try (InputStream is = altStream) {
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
						log.error("Failed to embed image '{}': {}", src, e.getMessage(), e);
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
		return "image/jpeg"; // default
	}

	// Method tambahan untuk debug - panggil saat startup
	@PostConstruct
	public void debugImageResources() {
		log.info("=== Debugging Image Resources ===");

		try {
			// Cek dengan ClassPathResource
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
			// Cek dengan ClassLoader
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
			// Cek dengan ResourceLoader
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
