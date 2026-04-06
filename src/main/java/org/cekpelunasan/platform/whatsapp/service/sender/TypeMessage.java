package org.cekpelunasan.platform.whatsapp.service.sender;

/**
 * Daftar jenis pesan yang bisa dikirim lewat WhatsApp gateway.
 * <p>
 * Setiap jenis pesan memiliki endpoint API yang berbeda.
 * Enum ini dipakai oleh {@link WhatsAppSender#buildPath(TypeMessage)}
 * untuk menentukan URL endpoint yang tepat saat mengirim pesan.
 * </p>
 */
public enum TypeMessage {
	/** Pesan teks biasa. */
	TEXT,
	/** Pesan berisi gambar. */
	IMAGE,
	/** Pesan berisi video. */
	VIDEO,
	/** Reaksi emoji ke pesan tertentu. */
	REACTION,
	/** Edit atau update isi pesan yang sudah terkirim. */
	UPDATE,
	/** Hapus pesan yang sudah terkirim. */
	DELETE,
	/** Pesan berisi file/dokumen. */
	FILE
}
