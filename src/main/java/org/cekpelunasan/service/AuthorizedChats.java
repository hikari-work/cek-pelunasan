package org.cekpelunasan.service;

import org.cekpelunasan.entity.User;
import org.cekpelunasan.repository.UserRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AuthorizedChats {

    private final UserRepository userRepository;
    Set<Long> authorizedChats = ConcurrentHashMap.newKeySet();

    public AuthorizedChats(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    public boolean isAuthorized(Long chatId) {
        return authorizedChats.contains(chatId);
    }
    public void addAuthorizedChat(Long chatId) {
        authorizedChats.add(chatId);
    }
    public void deleteUser(Long chatId) {
        authorizedChats.remove(chatId);
    }
    @EventListener(ApplicationReadyEvent.class)
    public void preRun() {
        List<User> all = userRepository.findAll();
        for (User user : all) {
            authorizedChats.add(user.getChatId());
        }
    }
}
