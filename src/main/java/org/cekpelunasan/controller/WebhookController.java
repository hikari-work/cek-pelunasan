package org.cekpelunasan.controller;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.platform.whatsapp.dto.webhook.WhatsAppWebhookDTO;
import org.cekpelunasan.platform.whatsapp.service.Routers;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
public class WebhookController {

    private final Routers routers;

    @PostMapping("/v2/whatsapp")
    public Mono<ResponseEntity<String>> whatsappV2(@RequestBody WhatsAppWebhookDTO dto) {
        routers.handle(dto);
        return Mono.just(ResponseEntity.ok("OK"));
    }
}
