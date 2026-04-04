package org.cekpelunasan.utils;

import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Slf4j
public class CsvDownloadUtils {

	private static final String UPLOAD_DIRECTORY = "files";

	/**
	 * Downloads a CSV file from the given URL and returns the local Path.
	 * Throws IllegalArgumentException if the file is not a CSV.
	 */
	public static Path downloadCsv(String fileUrl) throws Exception {
		String fileName = extractFileName(fileUrl);
		if (!fileName.endsWith(".csv")) {
			throw new IllegalArgumentException("File bukan CSV: " + fileName);
		}
		Path outputPath = Paths.get(UPLOAD_DIRECTORY, fileName);
		Files.createDirectories(outputPath.getParent());
		try (InputStream inputStream = URI.create(fileUrl).toURL().openStream()) {
			Files.copy(inputStream, outputPath, StandardCopyOption.REPLACE_EXISTING);
		}
		log.info("File downloaded: {}", outputPath.getFileName());
		return outputPath;
	}

	/**
	 * Extracts the URL from a command text like "/command https://...".
	 * Returns null if no URL is present.
	 */
	public static String extractUrl(String text) {
		String[] parts = text.split(" ", 2);
		return parts.length >= 2 ? parts[1].trim() : null;
	}

	public static String extractFileName(String fileUrl) {
		return fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
	}
}
