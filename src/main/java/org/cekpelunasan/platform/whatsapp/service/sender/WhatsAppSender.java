package org.cekpelunasan.platform.whatsapp.service.sender;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.platform.whatsapp.dto.send.BaseMessageRequestDTO;
import org.cekpelunasan.platform.whatsapp.dto.send.GenericResponseDTO;
import org.cekpelunasan.platform.whatsapp.dto.send.MessageActionDTO;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Lapisan paling bawah untuk komunikasi dengan WhatsApp gateway via HTTP.
 * <p>
 * Class ini bertugas satu hal saja: mengirim request POST ke endpoint WhatsApp gateway.
 * Ada dua jenis request yang ditangani: pengiriman pesan baru (menggunakan {@link BaseMessageRequestDTO})
 * dan aksi pada pesan yang sudah ada seperti update, hapus, atau reaksi (menggunakan {@link MessageActionDTO}).
 * </p>
 * <p>
 * Kalau request gagal karena error jaringan atau server, hasilnya adalah
 * {@code Mono.empty()} supaya proses di atasnya tidak crash — error cukup dicatat di log saja.
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WhatsAppSender {

    private final WebClient whatsappWebClient;

    /**
     * Menentukan path endpoint API berdasarkan jenis pesan yang akan dikirim.
     * <p>
     * Untuk aksi pada pesan yang sudah ada (REACTION, UPDATE, DELETE),
     * path yang dikembalikan masih mengandung placeholder {@code {message_id}}
     * yang harus diganti dengan ID pesan yang sebenarnya sebelum request dikirim.
     * </p>
     *
     * @param messageType jenis pesan atau aksi yang akan dilakukan
     * @return path URL endpoint yang sesuai
     */
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

    /**
     * Mengirim pesan baru ke WhatsApp gateway.
     *
     * @param path path endpoint API yang sudah ditentukan lewat {@link #buildPath(TypeMessage)}
     * @param dto  data pesan yang akan dikirim
     * @return response dari gateway, atau {@code Mono.empty()} kalau gagal
     */
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

    /**
     * Menjalankan aksi pada pesan yang sudah ada (update, hapus, atau reaksi).
     * <p>
     * Placeholder {@code {message_id}} di path akan diganti otomatis dengan
     * ID pesan dari DTO sebelum request dikirim.
     * </p>
     *
     * @param path path endpoint yang masih mengandung {@code {message_id}}
     * @param dto  data aksi yang berisi ID pesan dan data tambahan (misal emoji untuk reaksi)
     * @return response dari gateway, atau {@code Mono.empty()} kalau gagal
     */
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
