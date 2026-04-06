package org.cekpelunasan.configuration;

import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides a factory for creating short-lived Playwright Browser instances.
 * Browser is created per-request to avoid shared-state crashes.
 */
@Configuration
public class PlaywrightConfiguration {

	@Bean
	public BrowserType.LaunchOptions playwrightLaunchOptions() {
		return new BrowserType.LaunchOptions().setHeadless(true);
	}
}
