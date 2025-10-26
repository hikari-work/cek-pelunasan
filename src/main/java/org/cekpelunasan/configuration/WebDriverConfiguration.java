package org.cekpelunasan.configuration;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.print.PageMargin;
import org.openqa.selenium.print.PageSize;
import org.openqa.selenium.print.PrintOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

/*
Setting Selenium WebDriver to run headless.
Setting page size to landscape and generate print options.
 */

@Configuration
public class WebDriverConfiguration {

	@Bean
	public WebDriver chromeOptions() {
		ChromeOptions options = new ChromeOptions();
		options.addArguments("--headless=new");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-gpu");
		options.addArguments("--disable-dev-shm-usage");

		Path tmpProfile;
		try {
			tmpProfile = Files.createTempDirectory("chrome-profile-");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		options.addArguments("--user-data-dir=" + tmpProfile.toAbsolutePath());

		HashMap<String, Object> chromePrefs = new HashMap<>();
		chromePrefs.put("printing.default_destination_selection_rules", "{\"kind\": \"local\", \"namePattern\": \"Save as PDF\"}");
		chromePrefs.put("printing.print_preview_sticky_settings.appState",
			"{\"recentDestinations\":[{\"id\":\"Save as PDF\",\"origin\":\"local\"}]," +
				"\"selectedDestinationId\":\"Save as PDF\",\"version\":2," +
				"\"isLandscapeEnabled\":true,\"isHeaderFooterEnabled\":false}");
		options.setExperimentalOption("prefs", chromePrefs);
		return new ChromeDriver(options);
	}

	@Bean
	public PrintOptions printOptions() {
		PageMargin margin = new PageMargin(0.5F, 0.5F, 0.5F, 0.5F);
		PageSize pageSize = new PageSize(29.7, 21);
		PrintOptions printOptions = new PrintOptions();
		printOptions.setBackground(true);
		printOptions.setPageSize(pageSize);
		printOptions.setPageRanges("1-");
		printOptions.setOrientation(PrintOptions.Orientation.LANDSCAPE);
		printOptions.setPageMargin(margin);
		return printOptions;
	}
}
