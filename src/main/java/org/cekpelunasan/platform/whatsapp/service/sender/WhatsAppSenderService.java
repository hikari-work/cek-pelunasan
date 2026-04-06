package org.cekpelunasan.platform.whatsapp.service.sender;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.platform.whatsapp.dto.send.*;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Menyediakan method-method praktis untuk mengirim berbagai jenis pesan WhatsApp.
 * <p>
 * Class ini adalah antarmuka yang dipakai oleh semua service lain ketika ingin
 * mengirim sesuatu ke WhatsApp. Daripada harus membuat DTO sendiri dan memanggil
 * {@link WhatsAppSender} langsung, cukup panggil method yang sesuai di sini.
 * Tersedia method untuk kirim teks biasa, kirim teks sebagai balasan (reply),
 * edit pesan yang sudah terkirim, dan kirim reaksi emoji acak ke sebuah pesan.
 * </p>
 */
@Component
@RequiredArgsConstructor
public class WhatsAppSenderService {

	private final WhatsAppSender sender;

	/**
	 * Mengirim pesan teks biasa ke nomor WhatsApp yang dituju.
	 *
	 * @param phone   nomor tujuan dalam format yang diterima gateway (misal "6281234567890@s.whatsapp.net")
	 * @param message isi pesan yang akan dikirim
	 * @return Mono berisi response dari gateway
	 */
	public Mono<GenericResponseDTO> sendWhatsAppText(String phone, String message) {
		SendTextMessageDTO dto = new SendTextMessageDTO();
		dto.setPhone(phone);
		dto.setMessage(message);
		return sender.request(sender.buildPath(TypeMessage.TEXT), dto);
	}

	/**
	 * Mengirim pesan teks sebagai balasan (quote) dari pesan tertentu.
	 *
	 * @param phone          nomor tujuan
	 * @param message        isi pesan
	 * @param replyMessageId ID pesan yang ingin dibalas, atau {@code null} kalau tidak perlu quote
	 * @return Mono berisi response dari gateway
	 */
	public Mono<GenericResponseDTO> sendWhatsAppText(String phone, String message, String replyMessageId) {
		SendTextMessageDTO dto = new SendTextMessageDTO();
		dto.setPhone(phone);
		dto.setMessage(message);
		dto.setReplyMessageId(replyMessageId);
		return sender.request(sender.buildPath(TypeMessage.TEXT), dto);
	}

	/**
	 * Mengedit isi pesan yang sudah terkirim sebelumnya.
	 * Berguna untuk mengganti pesan "sedang diproses" dengan hasil yang sudah jadi.
	 *
	 * @param phone     nomor chat tempat pesan berada
	 * @param messageId ID pesan yang akan diedit
	 * @param message   isi baru pesan yang akan menggantikan yang lama
	 * @return Mono berisi response dari gateway
	 */
	public Mono<GenericResponseDTO> updateMessage(String phone, String messageId, String message) {
		MessageUpdateDTO dto = new MessageUpdateDTO();
		dto.setPhone(phone);
		dto.setMessageId(messageId);
		dto.setMessage(message);
		return sender.request(sender.buildPath(TypeMessage.UPDATE), dto);
	}

	/**
	 * Mengirim reaksi emoji acak ke sebuah pesan sebagai tanda pesan sudah diproses.
	 * <p>
	 * Emoji dipilih secara acak dari daftar: 👌, ✍, 🙏, 👍, 🤝, 👊.
	 * Ini dipakai sebagai umpan balik visual kepada pengguna bahwa perintahnya sedang dikerjakan.
	 * </p>
	 *
	 * @param phone     nomor chat tempat pesan berada
	 * @param messageId ID pesan yang akan diberi reaksi
	 * @return Mono berisi response dari gateway
	 */
	public Mono<GenericResponseDTO> sendReactionToMessage(String phone, String messageId) {
		MessageReactionDTO dto = new MessageReactionDTO();
		dto.setPhone(phone);
		dto.setMessageId(messageId);
		List<String> reactions = List.of("👌", "✍", "🙏", "👍", "🤝", "👊");
		dto.setEmoji(reactions.get((int) (Math.random() * reactions.size())));
		return sender.request(sender.buildPath(TypeMessage.REACTION), dto);
	}
}
