package org.cekpelunasan.platform.whatsapp.service.slik;

import org.cekpelunasan.platform.whatsapp.dto.webhook.WhatsAppWebhookDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.List;

/**
 * Menangani pencarian dan pengiriman file data SLIK dari penyimpanan cloud (Cloudflare R2).
 * <p>
 * Ketika admin mengetik ".s [nama nasabah]", service ini mencari file SLIK yang
 * namanya mengandung kata kunci tersebut di bucket R2. File yang ditemukan bisa
 * berupa file teks (.txt) yang perlu dikonversi ke PDF dulu, atau file PDF yang
 * langsung bisa dikirim.
 * </p>
 * <p>
 * Catatan: Fitur pengiriman PDF dan konversi TXT ke PDF masih dalam pengembangan (TODO).
 * </p>
 */
@Component
public class SlikService {

	@Value("${r2.bucket}")
	private String bucket;

	private final S3AsyncClient s3AsyncClient;

	public SlikService(S3AsyncClient s3AsyncClient) {
		this.s3AsyncClient = s3AsyncClient;
	}

	/**
	 * Memproses perintah cek SLIK — mencari file di bucket dan menentukan aksi selanjutnya.
	 *
	 * @param webhookDTO data webhook dari perintah SLIK yang masuk
	 */
	public void handleSlikService(WhatsAppWebhookDTO webhookDTO) {
		String text = webhookDTO.getPayload().getBody().substring(".s ".length());
		String fileName = getMatchingItems(text, getBucketList());
		if (fileName == null) {
			return;
		}
		if (fileName.endsWith(".txt")) {
			// TODO : Generate PDF Files
		} else if (fileName.endsWith(".pdf")) {
			// TODO : Send PDF Files
		}
	}

	/**
	 * Mengambil semua nama file yang ada di bucket R2.
	 * Kalau ada error (jaringan, akses, dll.), method ini mengembalikan list kosong.
	 *
	 * @return daftar nama file di bucket, atau list kosong kalau gagal mengambil data
	 */
	public List<String> getBucketList() {
		try {
			ListObjectsV2Request request = ListObjectsV2Request.builder().bucket(bucket).build();
			ListObjectsV2Response response = s3AsyncClient.listObjectsV2(request).get();
			return response.contents().stream().map(S3Object::key).toList();
		} catch (Exception e) {
			return List.of();
		}
	}

	/**
	 * Mencari nama file di daftar yang mengandung kata kunci tertentu (case-insensitive).
	 *
	 * @param name  kata kunci pencarian (nama nasabah atau sebagian nama file)
	 * @param items daftar nama file yang akan dicari
	 * @return nama file pertama yang cocok, atau {@code null} kalau tidak ada yang sesuai
	 */
	public String getMatchingItems(String name, List<String> items) {
		return items.stream()
			.filter(item -> item.toLowerCase().contains(name.toLowerCase()))
			.findFirst()
			.orElse(null);
	}
}
