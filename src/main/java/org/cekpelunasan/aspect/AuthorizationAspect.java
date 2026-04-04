package org.cekpelunasan.aspect;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.core.service.auth.AuthorizedChats;
import org.cekpelunasan.platform.telegram.service.TelegramMessageService;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

import java.util.Arrays;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuthorizationAspect {

    private final AuthorizedChats authorizedChats;
    private final TelegramMessageService telegramMessageService;

    @Around("@annotation(requireAuth)")
    public Object checkAuth(@NotNull ProceedingJoinPoint joinPoint, RequireAuth requireAuth) throws Throwable {
        Object[] args = joinPoint.getArgs();
        TdApi.UpdateNewMessage update = null;
        SimpleTelegramClient client = null;
        for (Object arg : args) {
            if (arg instanceof TdApi.UpdateNewMessage u) {
                update = u;
            } else if (arg instanceof SimpleTelegramClient c) {
                client = c;
            }
        }
        if (update == null || client == null) {
            return joinPoint.proceed();
        }
        long chatId = update.message.chatId;
        if (!authorizedChats.isAuthorized(chatId)) {
            telegramMessageService.sendText(chatId, "Anda tidak memiliki akses ke bot ini", client);
            return Mono.empty();
        }
        AccountOfficerRoles roles = authorizedChats.getUserRoles(chatId).block();
        if (roles == AccountOfficerRoles.ADMIN) {
            return joinPoint.proceed();
        }
        AccountOfficerRoles[] requiredRoles = requireAuth.roles();
        boolean hasRequiredRoles = Arrays.stream(requiredRoles).anyMatch(role -> role == roles);
        if (!hasRequiredRoles) {
            telegramMessageService.sendText(chatId, "Anda tidak memiliki akses ke bot ini", client);
            return Mono.empty();
        }
        return joinPoint.proceed();
    }
}
