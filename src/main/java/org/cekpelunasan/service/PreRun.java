package org.cekpelunasan.service;

import org.cekpelunasan.entity.User;
import org.cekpelunasan.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class PreRun {


    private final Long botOwner;

    private final UserRepository userRepository;

    public PreRun(UserRepository userRepository, @Value("${telegram.bot.owner}") String botOwner) {
        this.botOwner = Long.parseLong(botOwner);
        this.userRepository = userRepository;
    }
    @EventListener(ApplicationReadyEvent.class)
    public void initData() {
        User user = User.builder()
                .chatId(botOwner)
                .username("ADMIN")
                .build();
        userRepository.save(user);
    }
}
