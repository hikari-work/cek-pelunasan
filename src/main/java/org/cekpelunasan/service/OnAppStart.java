package org.cekpelunasan.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class OnAppStart implements ApplicationRunner {

	private final NgrokService ngrokService;

	public OnAppStart(NgrokService ngrokService) {
		this.ngrokService = ngrokService;
	}


	@Override
	public void run(ApplicationArguments args) {
		ngrokService.setWebhook();
	}
}
