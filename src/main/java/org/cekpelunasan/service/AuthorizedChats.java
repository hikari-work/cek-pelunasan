package org.cekpelunasan.service;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AuthorizedChats {

    Set<Long> authorizedChats = ConcurrentHashMap.newKeySet();

    public boolean isAuthorized(Long chatId) {
        return authorizedChats.contains(chatId);
    }
    public void addAuthorizedChat(Long chatId) {
        authorizedChats.add(chatId);
    }
}
