package org.cekpelunasan.service;

import org.cekpelunasan.entity.AccountOfficerRoles;
import org.cekpelunasan.entity.User;
import org.cekpelunasan.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class PreRun {


	private static final Logger log = LoggerFactory.getLogger(PreRun.class);
	private final Long botOwner;

	private final UserRepository userRepository;

	public PreRun(UserRepository userRepository, @Value("${telegram.bot.owner}") String botOwner) {
		this.botOwner = Long.parseLong(botOwner);
		this.userRepository = userRepository;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void initData() {
		log.info("Initializing data...");
		User user = User.builder()
			.chatId(botOwner)
			.userCode("ADMIN")
			.roles(AccountOfficerRoles.ADMIN)
			.build();
		userRepository.save(user);
	}
}
