package org.cekpelunasan.platform.whatsapp.service.sender;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.platform.whatsapp.dto.send.BaseMessageRequestDTO;
import org.cekpelunasan.platform.whatsapp.dto.send.GenericResponseDTO;
import org.cekpelunasan.platform.whatsapp.dto.send.MessageActionDTO;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class WhatsAppSender {

    private final WebClient whatsappWebClient;

    public String buildPath(TypeMessage messageType) {
        return switch (messageType) {
            case TEXT -> "/send/message";
            case IMAGE -> "/send/image";
            case VIDEO -> "/send/video";
            case REACTION -> "/message/{message_id}/reaction";
            case UPDATE -> "/message/{message_id}/update";
            case DELETE -> "/message/{message_id}/delete";
            case FILE -> "/send/file";
        };
    }

    public Mono<GenericResponseDTO> request(String path, BaseMessageRequestDTO dto) {
        return whatsappWebClient.post()
                .uri(path)
                .bodyValue(dto)
                .retrieve()
                .bodyToMono(GenericResponseDTO.class)
                .onErrorResume(e -> {
                    log.error("Error sending WhatsApp request to {}: {}", path, e.getMessage());
                    return Mono.empty();
                });
    }

    public Mono<GenericResponseDTO> request(String path, MessageActionDTO dto) {
        String resolvedPath = path.replace("{message_id}", dto.getMessageId());
        return whatsappWebClient.post()
                .uri(resolvedPath)
                .bodyValue(dto)
                .retrieve()
                .bodyToMono(GenericResponseDTO.class)
                .onErrorResume(e -> {
                    log.error("Error sending WhatsApp action request to {}: {}", resolvedPath, e.getMessage());
                    return Mono.empty();
                });
    }
}
