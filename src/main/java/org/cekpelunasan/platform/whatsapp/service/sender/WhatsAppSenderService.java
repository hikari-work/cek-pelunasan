package org.cekpelunasan.platform.whatsapp.service.sender;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.platform.whatsapp.dto.send.*;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class WhatsAppSenderService {

	private final WhatsAppSender sender;

	@SuppressWarnings("UnusedReturnValue")
	public GenericResponseDTO sendWhatsAppText(String phone, String message) {
		SendTextMessageDTO dto = new SendTextMessageDTO();
		dto.setPhone(phone);
		dto.setMessage(message);
		return sender.request(sender.buildPath(TypeMessage.TEXT), dto).block();
	}

	@SuppressWarnings("UnusedReturnValue")
	public GenericResponseDTO sendWhatsAppText(String phone, String message, String replyMessageId) {
		SendTextMessageDTO dto = new SendTextMessageDTO();
		dto.setPhone(phone);
		dto.setMessage(message);
		dto.setReplyMessageId(replyMessageId);
		return sender.request(sender.buildPath(TypeMessage.TEXT), dto).block();
	}

	@SuppressWarnings("UnusedReturnValue")
	public GenericResponseDTO updateMessage(String phone, String messageId, String message) {
		MessageUpdateDTO dto = new MessageUpdateDTO();
		dto.setPhone(phone);
		dto.setMessageId(messageId);
		dto.setMessage(message);
		return sender.request(sender.buildPath(TypeMessage.UPDATE), dto).block();
	}

	public GenericResponseDTO sendReactionToMessage(String phone, String messageId) {
		MessageReactionDTO dto = new MessageReactionDTO();
		dto.setPhone(phone);
		dto.setMessageId(messageId);
		List<String> reactions = List.of("👌", "✍", "🙏", "👍", "🤝", "👊");
		dto.setEmoji(reactions.get((int) (Math.random() * reactions.size())));
		return sender.request(sender.buildPath(TypeMessage.REACTION), dto).block();
	}
}
