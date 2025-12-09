package org.cekpelunasan.aspect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.entity.AccountOfficerRoles;
import org.cekpelunasan.service.auth.AuthorizedChats;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.Arrays;

/**
 * Aspect for handling authorization logic.
 * <p>
 * This aspect intercepts methods annotated with {@link RequireAuth} to verify user permissions.
 * It checks if the chat ID is authorized and if the user possesses the required roles.
 * </p>
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuthorizationAspect {

	private final AuthorizedChats authorizedChats;
	private final TelegramClient telegramClientSender;

	/**
	 * Intercepts method execution to perform authorization checks.
	 *
	 * @param joinPoint   The join point representing the intercepted method.
	 * @param requireAuth The {@link RequireAuth} annotation instance containing the required roles.
	 * @return The result of the method execution or {@code null} if authorization fails.
	 * @throws Throwable If any error occurs during method execution.
	 */
	@Around("@annotation(requireAuth)")
	public Object checkAuth(@NotNull ProceedingJoinPoint joinPoint, RequireAuth requireAuth) throws Throwable {
		Object[] args = joinPoint.getArgs();
		Update update = null;
		TelegramClient telegramClient = null;
		for (Object arg : args) {
			if (arg instanceof Update) {
				update = (Update) arg;
			} else if (arg instanceof TelegramClient) {
				telegramClient = (TelegramClient) arg;
			}
		}
		if (update == null || telegramClient == null) {
			log.error("Update or TelegramClient is null");
			return joinPoint.proceed();
		}
		long chatId = update.getMessage().getChatId();
		if (!authorizedChats.isAuthorized(chatId)) {
			log.warn("User {} is not authorized", chatId);
			telegramClientSender.executeAsync(SendMessage.builder()
					.chatId(chatId)
					.text("Anda tidak memiliki akses ke bot ini")
				.build());
			return null;
		}
		AccountOfficerRoles roles = authorizedChats.getUserRoles(chatId);
		AccountOfficerRoles[] requiredRoles = requireAuth.roles();
		boolean hasRequiredRoles = Arrays.stream(requiredRoles)
			.anyMatch(role -> role == roles);
		if (!hasRequiredRoles) {
			log.warn("User {} has no required roles: {}", chatId, requiredRoles);
			telegramClientSender.executeAsync(SendMessage.builder()
					.chatId(chatId)
					.text("Anda tidak memiliki akses ke bot ini")
				.build());
			return null;
		}
		
		return joinPoint.proceed();
	}
}
