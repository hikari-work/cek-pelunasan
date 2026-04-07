package org.cekpelunasan.miniapp.filter;

import org.cekpelunasan.miniapp.auth.MiniAppSession;
import org.cekpelunasan.miniapp.auth.MiniAppSessionStore;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Filter yang memvalidasi session token Mini App untuk semua endpoint {@code /api/mini/**}
 * kecuali {@code /api/mini/auth}. Token dibaca dari header {@code X-Mini-Token}.
 * Jika token tidak ada atau kedaluwarsa, langsung mengembalikan 401.
 */
@Component
public class MiniAppAuthFilter implements WebFilter {

    private static final String SESSION_ATTR = "miniAppSession";
    private static final String TOKEN_HEADER = "X-Mini-Token";
    private static final String MINI_PATH_PREFIX = "/api/mini/";
    private static final String AUTH_PATH = "/api/mini/auth";

    private final MiniAppSessionStore sessionStore;

    public MiniAppAuthFilter(MiniAppSessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (!path.startsWith(MINI_PATH_PREFIX) || path.equals(AUTH_PATH)) {
            return chain.filter(exchange);
        }

        String token = exchange.getRequest().getHeaders().getFirst(TOKEN_HEADER);
        Optional<MiniAppSession> session = sessionStore.get(token);

        if (session.isEmpty()) {
            return unauthorized(exchange);
        }

        exchange.getAttributes().put(SESSION_ATTR, session.get());
        return chain.filter(exchange);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = "{\"error\":\"Unauthorized\",\"message\":\"Token tidak valid atau sudah kedaluwarsa\"}"
                .getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
