package org.cekpelunasan.service.users;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.cekpelunasan.entity.User;
import org.cekpelunasan.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;

	@Transactional
	@SuppressWarnings("null")
	public void insertNewUsers(@NonNull Long chatId) {
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
	@SuppressWarnings("null")
	public void deleteUser(@NonNull Long chatId) {
		userRepository.deleteById(chatId);
	}

	@Transactional
	@SuppressWarnings("null")
	public Optional<User> findUserByChatId(@NonNull Long chatId) {
		return userRepository.findById(chatId);
	}

	@Transactional
	@SuppressWarnings("null")
	public String findUserBranch(@NonNull Long chatId) {
		Optional<User> byId = userRepository.findById(chatId);
		return byId.map(User::getBranch).orElse(null);
	}

	@Transactional
	@SuppressWarnings("null")
	public void saveUserBranch(@NonNull Long chatId, String branch) {
		userRepository.findById(chatId).ifPresent(user -> {
			user.setBranch(branch);
			userRepository.save(user);
		});
	}
}
