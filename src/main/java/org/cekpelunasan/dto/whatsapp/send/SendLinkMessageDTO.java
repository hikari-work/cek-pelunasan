package org.cekpelunasan.dto.whatsapp.send;

import lombok.*;


/**
 * DTO for sending a link message.
 * <p>
 * This class represents a request to send a URL link with a caption.
 * </p>
 */
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SendLinkMessageDTO extends BaseMessageRequestDTO {
	/**
	 * The URL to be sent.
	 */
	private String link;
	/**
	 * The caption to accompany the link preview.
	 */
	private String caption;
}
