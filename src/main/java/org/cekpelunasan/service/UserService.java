package org.cekpelunasan.service;

import org.cekpelunasan.entity.User;
import org.cekpelunasan.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void insertNewUser(Long chatId) {
        if (userRepository.findById(chatId).isEmpty()) {
            userRepository.save(User.builder()
                    .chatId(chatId)
                    .build());
        }

    }
    public List<User> findAllUsers() {
        return userRepository.findAll();
    }
    public Long countUsers() {
        return userRepository.count();
    }
    public User findUser(Long chatId) {
        return userRepository.findById(chatId).orElse(null);
    }
}
