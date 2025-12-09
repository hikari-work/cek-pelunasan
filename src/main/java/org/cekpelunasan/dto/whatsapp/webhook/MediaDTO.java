package org.cekpelunasan.dto.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Media DTO untuk berbagai jenis media
/**
 * DTO for media information received via webhook.
 * <p>
 * This class handles various types of media (image, video, audio) received in
 * messages.
 * </p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MediaDTO {
	/**
	 * The path or URL of the media file.
	 */
	@JsonProperty("media_path")
	private String mediaPath;

	/**
	 * The MIME type of the media.
	 */
	@JsonProperty("mime_type")
	private String mimeType;

	/**
	 * The caption associated with the media.
	 */
	private String caption;
}
