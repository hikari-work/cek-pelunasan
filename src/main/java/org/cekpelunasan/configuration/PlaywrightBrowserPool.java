package org.cekpelunasan.configuration;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

@Component
public class PlaywrightBrowserPool {

	private static final Logger log = LoggerFactory.getLogger(PlaywrightBrowserPool.class);
	private static final int POOL_SIZE = 3;

	private final Playwright playwright;
	private final BrowserType.LaunchOptions launchOptions;
	private final BlockingQueue<Browser> pool = new ArrayBlockingQueue<>(POOL_SIZE);

	public PlaywrightBrowserPool(BrowserType.LaunchOptions playwrightLaunchOptions) {
		this.playwright = Playwright.create();
		this.launchOptions = playwrightLaunchOptions;
		for (int i = 0; i < POOL_SIZE; i++) {
			pool.offer(launchBrowser());
		}
		log.info("Playwright browser pool initialized with {} instances", POOL_SIZE);
	}

	public Browser acquire() throws InterruptedException {
		return pool.take();
	}

	public void release(Browser browser) {
		if (browser != null && browser.isConnected()) {
			pool.offer(browser);
		} else {
			log.warn("Browser disconnected or null, replacing with fresh instance");
			synchronized (this) {
				try {
					pool.offer(launchBrowser());
				} catch (Exception e) {
					log.error("Failed to replace crashed browser in pool", e);
				}
			}
		}
	}

	private synchronized Browser launchBrowser() {
		return playwright.chromium().launch(launchOptions);
	}

	@PreDestroy
	public void shutdown() {
		Browser b;
		while ((b = pool.poll()) != null) {
			try { b.close(); } catch (Exception ignored) {}
		}
		try { playwright.close(); } catch (Exception ignored) {}
		log.info("Playwright browser pool shut down");
	}
}
