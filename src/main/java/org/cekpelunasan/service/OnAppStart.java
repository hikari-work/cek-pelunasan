package org.cekpelunasan.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OnAppStart implements ApplicationRunner {

	private final NgrokService ngrokService;

	@Override
	public void run(ApplicationArguments args) {
		ngrokService.setWebhook();
	}
}
