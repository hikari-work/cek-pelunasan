package org.cekpelunasan.dto.whatsapp.send;

import lombok.*;

/**
 * DTO for sending image messages.
 * <p>
 * This class represents a request to send an image URL with an optional
 * caption.
 * </p>
 */
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SendImageMessageDTO extends BaseMessageRequestDTO {
	/**
	 * Caption text for the image.
	 */
	private String caption;
	/**
	 * Whether the image should be viewable only once. Default is false.
	 */
	private Boolean viewOnce = false;
	/**
	 * The URL of the image.
	 */
	private String imageUrl;
	/**
	 * Whether to compress the image. Default is false.
	 */
	private Boolean compress = false;

}
