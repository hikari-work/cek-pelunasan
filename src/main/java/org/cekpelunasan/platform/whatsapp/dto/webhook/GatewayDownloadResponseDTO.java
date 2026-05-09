package org.cekpelunasan.platform.whatsapp.dto.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response dari endpoint gateway GET /message/{id}/download?phone=xxx.
 * Berisi path lokal file yang sudah didownload oleh gateway.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GatewayDownloadResponseDTO {

    @JsonProperty("message_id")
    private String messageId;

    private String status;

    @JsonProperty("media_type")
    private String mediaType;

    private String filename;

    @JsonProperty("file_path")
    private String filePath;

    @JsonProperty("file_size")
    private Long fileSize;
}
