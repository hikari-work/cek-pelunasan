package org.cekpelunasan.dto.whatsapp.send;

import lombok.*;

/**
 * DTO for sending a sticker message.
 * <p>
 * This class represents a request to send a sticker via URL.
 * </p>
 */
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SendStickerMessageDTO extends BaseMessageRequestDTO {
	/**
	 * The URL of the sticker image.
	 */
	private String stickerUrl;

}
