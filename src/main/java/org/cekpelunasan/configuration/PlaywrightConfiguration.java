package org.cekpelunasan.configuration;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides singleton Playwright + Browser beans.
 * Page instances are created per-request inside GeneratePdfFiles.
 */
@Configuration
public class PlaywrightConfiguration {

	private static final Logger log = LoggerFactory.getLogger(PlaywrightConfiguration.class);

	@Bean(destroyMethod = "close")
	public Playwright playwright() {
		log.info("Initializing Playwright...");
		return Playwright.create();
	}

	@Bean(destroyMethod = "close")
	public Browser playwrightBrowser(Playwright playwright) {
		log.info("Launching Chromium browser (headless)...");
		return playwright.chromium().launch(
			new BrowserType.LaunchOptions().setHeadless(true)
		);
	}
}
