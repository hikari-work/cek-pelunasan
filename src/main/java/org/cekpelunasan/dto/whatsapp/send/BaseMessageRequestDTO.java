package org.cekpelunasan.dto.whatsapp.send;

import lombok.Getter;
import lombok.Setter;

/**
 * Base class for all message request DTOs.
 * <p>
 * This abstract class provides common fields required for sending messages via
 * WhatsApp.
 * </p>
 */
@Setter
@Getter
public abstract class BaseMessageRequestDTO {
	/**
	 * The phone number of the recipient.
	 */
	private String phone;
	/**
	 * Indicates if the message is forwarded. Default is false.
	 */
	private Boolean isForwarded = false;
	/**
	 * Duration of the message (e.g., audio or video) in seconds.
	 */
	private Integer duration;

	/**
	 * Default constructor.
	 */
	public BaseMessageRequestDTO() {
	}

	/**
	 * Constructor with phone number.
	 *
	 * @param phone The recipient's phone number.
	 */
	public BaseMessageRequestDTO(String phone) {
		this.phone = phone;
	}
}
