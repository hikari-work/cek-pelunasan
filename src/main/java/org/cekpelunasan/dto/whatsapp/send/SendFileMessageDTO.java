package org.cekpelunasan.dto.whatsapp.send;

import lombok.*;

/**
 * Generic DTO for sending file-based messages.
 * <p>
 * This class is a comprehensive DTO that can handle various file types and
 * potentially other message types.
 * Note: Some fields might be mutually exclusive depending on the message type.
 * </p>
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SendFileMessageDTO extends BaseMessageRequestDTO {
	/**
	 * Caption for media messages.
	 */
	private String caption;
	/**
	 * Raw bytes of the file (if uploading directly).
	 */
	private byte[] fileBytes;
	/**
	 * Name of the file.
	 */
	private String fileName;
	/**
	 * Indicates if the message is forwarded.
	 */
	private Boolean isForwarded = false;
	/**
	 * Duration of media in seconds. Default is 3600.
	 */
	private Integer duration = 3600;
	/**
	 * URL of the video file.
	 */
	private String videoUrl;
	/**
	 * URL of the audio file.
	 */
	private String audioUrl;
	/**
	 * URL of the sticker.
	 */
	private String stickerUrl;
	/**
	 * Contact name for contact sharing.
	 */
	private String contactName;
	/**
	 * Contact phone number for contact sharing.
	 */
	private String contactPhone;
	/**
	 * URL associated with location sharing.
	 */
	private String locationUrl;
	/**
	 * Latitude for location sharing.
	 */
	private String latitude;
	/**
	 * Longitude for location sharing.
	 */
	private String longitude;
	/**
	 * URL link for link messages.
	 */
	private String link;
	/**
	 * URL of the image file.
	 */
	private String imageUrl;
}
