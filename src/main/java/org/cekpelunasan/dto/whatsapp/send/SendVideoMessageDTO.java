package org.cekpelunasan.dto.whatsapp.send;

import lombok.*;

/**
 * DTO for sending a video message.
 * <p>
 * This class represents a request to send a video file via URL, with options
 * for caption and compression.
 * </p>
 */
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SendVideoMessageDTO extends BaseMessageRequestDTO {
	/**
	 * Caption text for the video.
	 */
	private String caption;
	/**
	 * Whether the video should be viewable only once. Default is false.
	 */
	private Boolean viewOnce = false;
	/**
	 * The URL of the video file.
	 */
	private String videoUrl;
	/**
	 * Whether to compress the video. Default is false.
	 */
	private Boolean compress = false;

}
