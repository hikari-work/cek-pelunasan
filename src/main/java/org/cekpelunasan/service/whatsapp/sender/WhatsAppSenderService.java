package org.cekpelunasan.service.whatsapp.sender;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.dto.whatsapp.send.*;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class WhatsAppSenderService {

	private final WhatsAppSender sender;

	@SuppressWarnings("UnusedReturnValue")
	public GenericResponseDTO sendWhatsAppText(String phone, String message) {
		SendTextMessageDTO textMessageDTO = new SendTextMessageDTO();
		textMessageDTO.setPhone(phone);
		textMessageDTO.setMessage(message);
		String url = sender.buildUrl(TypeMessage.TEXT);
		ResponseEntity<GenericResponseDTO> response = sender.request(url, textMessageDTO);
		return response.getBody();
	}

	public GenericResponseDTO sendWhatsAppText(String phone, String message, String replyMessageId) {
		SendTextMessageDTO textMessageDTO = new SendTextMessageDTO();
		textMessageDTO.setPhone(phone);
		textMessageDTO.setMessage(message);
		textMessageDTO.setReplyMessageId(replyMessageId);
		String url = sender.buildUrl(TypeMessage.TEXT);
		ResponseEntity<GenericResponseDTO> response = sender.request(url, textMessageDTO);
		return response.getBody();
	}

	@SuppressWarnings("UnusedReturnValue")
	public GenericResponseDTO updateMessage(String phone, String messageId, String message) {
		MessageUpdateDTO update = new MessageUpdateDTO();
		update.setPhone(phone);
		update.setMessageId(messageId);
		update.setMessage(message);
		String url = sender.buildUrl(TypeMessage.UPDATE);
		ResponseEntity<GenericResponseDTO> response = sender.request(url, update);
		return response.getBody();
	}
	public GenericResponseDTO sendReactionToMessage(String phone, String messageId, String reaction) {
		MessageReactionDTO reactionDTO = new MessageReactionDTO();
		reactionDTO.setPhone(phone);
		reactionDTO.setMessageId(messageId);
		reactionDTO.setEmoji(reaction);
		String url = sender.buildUrl(TypeMessage.REACTION);
		ResponseEntity<GenericResponseDTO> response = sender.request(url, reactionDTO);
		return response.getBody();
	}

	public GenericResponseDTO sendReactionToMessage(String phone, String messageId) {
		MessageReactionDTO reactionDTO = new MessageReactionDTO();
		reactionDTO.setPhone(phone);
		reactionDTO.setMessageId(messageId);
		List<String> reaction = List.of("👌","✍","🙏", "👍", "🤝","👊");
		reactionDTO.setEmoji(reaction.get((int) (Math.random() * reaction.size())));
		String url = sender.buildUrl(TypeMessage.REACTION);
		ResponseEntity<GenericResponseDTO> response = sender.request(url, reactionDTO);
		return response.getBody();
	}

	public GenericResponseDTO deleteMessage(String phone, String messageId) {
		DeleteMessageDTO deleteMessageDTO = new DeleteMessageDTO();
		deleteMessageDTO.setPhone(phone);
		deleteMessageDTO.setMessageId(messageId);
		String url = sender.buildUrl(TypeMessage.DELETE);
		ResponseEntity<GenericResponseDTO> response = sender.request(url, deleteMessageDTO);
		return response.getBody();
	}


}
