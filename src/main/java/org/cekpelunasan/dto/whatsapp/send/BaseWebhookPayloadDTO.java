package org.cekpelunasan.dto.whatsapp.send;

import lombok.*;


public abstract class BaseWebhookPayloadDTO {
	private String senderId;
	private String chatId;
	private String from;
	private String timestamp;
	private String pushname;
	private MessageDTO message;


}
