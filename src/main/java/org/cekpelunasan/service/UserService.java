package org.cekpelunasan.service;

import org.cekpelunasan.entity.User;
import org.cekpelunasan.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;


    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    @Transactional
    public void insertNewUsers(Long chatId) {
        userRepository.save(User.builder()
                        .chatId(chatId)
                .build());
    }


    @Transactional
    public Long countUsers() {
        return userRepository.count();
    }

    @Transactional
    public List<User> findAllUsers() {
        return userRepository.findAll();
    }

    @Transactional
    public void deleteUser(Long chatId) {
        userRepository.deleteById(chatId);
    }
    @Transactional
    public void addUser(User user) {
        userRepository.save(user);
    }
    @Transactional
    public User findUserByChatId(Long chatId) {
        return userRepository.findById(chatId).orElse(null);
    }
}
