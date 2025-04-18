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
    public void insertNewUsers(Long chatId) {
        userRepository.save(User.builder()
                        .chatId(chatId)
                .build());
    }
    public User findUser(Long chatId) {
        return userRepository.findById(chatId).orElse(null);
    }

    public Long countUsers() {
        return userRepository.count();
    }

    public List<User> findAllUsers() {
        return userRepository.findAll();
    }

    public void deleteUser(Long chatId) {
        userRepository.deleteById(chatId);
    }
}
