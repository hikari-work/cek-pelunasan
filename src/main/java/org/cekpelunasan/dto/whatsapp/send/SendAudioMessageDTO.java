package org.cekpelunasan.dto.whatsapp.send;

import lombok.*;

/**
 * DTO for sending audio messages.
 * <p>
 * This class represents a request to send an audio file via URL.
 * </p>
 */
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SendAudioMessageDTO extends BaseMessageRequestDTO {
	/**
	 * The URL of the audio file to be sent.
	 */
	private String audioUrl;

}
