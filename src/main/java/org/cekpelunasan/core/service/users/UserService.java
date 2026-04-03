package org.cekpelunasan.core.service.users;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.cekpelunasan.core.entity.User;
import org.cekpelunasan.core.repository.UserRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;

	@SuppressWarnings("null")
	public Mono<Void> insertNewUsers(@NonNull Long chatId) {
		return userRepository.save(User.builder().chatId(chatId).build()).then();
	}

	public Mono<Long> countUsers() {
		return userRepository.count();
	}

	public Flux<User> findAllUsers() {
		return userRepository.findAll();
	}

	@SuppressWarnings("null")
	public Mono<Void> deleteUser(@NonNull Long chatId) {
		return userRepository.deleteById(chatId);
	}

	@SuppressWarnings("null")
	public Mono<User> findUserByChatId(@NonNull Long chatId) {
		return userRepository.findById(chatId);
	}

	@SuppressWarnings("null")
	public Mono<String> findUserBranch(@NonNull Long chatId) {
		return userRepository.findById(chatId)
			.flatMap(user -> Mono.justOrEmpty(user.getBranch()));
	}

	@SuppressWarnings("null")
	public Mono<Void> saveUserBranch(@NonNull Long chatId, String branch) {
		return userRepository.findById(chatId)
			.flatMap(user -> {
				user.setBranch(branch);
				return userRepository.save(user);
			})
			.then();
	}
}
