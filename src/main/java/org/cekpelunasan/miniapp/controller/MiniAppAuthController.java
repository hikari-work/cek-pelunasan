package org.cekpelunasan.miniapp.controller;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.core.service.auth.AuthorizedChats;
import org.cekpelunasan.core.service.users.UserService;
import org.cekpelunasan.miniapp.auth.MiniAppSession;
import org.cekpelunasan.miniapp.auth.MiniAppSessionStore;
import org.cekpelunasan.miniapp.auth.TelegramInitDataVerifier;
import org.cekpelunasan.miniapp.dto.MiniAppAuthRequest;
import org.cekpelunasan.miniapp.dto.MiniAppAuthResponse;
import org.cekpelunasan.miniapp.dto.UserInfoDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Endpoint autentikasi Mini App. Menerima {@code initData} dari Telegram WebApp,
 * memverifikasi tanda tangannya, mengecek apakah pengguna terdaftar di bot,
 * lalu mengeluarkan session token.
 */
@RestController
@RequestMapping("/api/mini")
@RequiredArgsConstructor
public class MiniAppAuthController {

    private final TelegramInitDataVerifier verifier;
    private final MiniAppSessionStore sessionStore;
    private final AuthorizedChats authorizedChats;
    private final UserService userService;

    @PostMapping("/auth")
    public Mono<ResponseEntity<MiniAppAuthResponse>> auth(@RequestBody MiniAppAuthRequest request) {
        TelegramInitDataVerifier.VerificationResult result = verifier.verify(request.initData());

        if (!result.valid()) {
            return Mono.just(ResponseEntity.status(401).<MiniAppAuthResponse>build());
        }

        Long chatId = result.chatId();

        if (!authorizedChats.isAuthorized(chatId)) {
            return Mono.just(ResponseEntity.status(403).<MiniAppAuthResponse>build());
        }

        return userService.findUserByChatId(chatId)
                .map(user -> {
                    MiniAppSession session = sessionStore.create(chatId, user.getRoles());
                    UserInfoDTO userInfo = new UserInfoDTO(chatId, result.firstName(), user.getRoles());
                    return ResponseEntity.ok(new MiniAppAuthResponse(session.token(), userInfo));
                })
                .defaultIfEmpty(ResponseEntity.status(403).<MiniAppAuthResponse>build());
    }
}
