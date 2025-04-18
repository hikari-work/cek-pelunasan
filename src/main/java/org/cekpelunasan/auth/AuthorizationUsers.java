package org.cekpelunasan.auth;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
public class AuthorizationUsers {

    public Set<Long> authorized = new HashSet<>();

    public boolean isAuthorized(Long chatId) {
        return authorized.contains(chatId);
    }

    public void addAuthorizedChat(Long chatId) {
        authorized.add(chatId);
    }
}
