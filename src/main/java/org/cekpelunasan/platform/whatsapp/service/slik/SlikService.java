package org.cekpelunasan.platform.whatsapp.service.slik;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.configuration.S3ClientConfiguration;
import org.cekpelunasan.core.service.slik.GeneratePdfFiles;
import org.cekpelunasan.core.service.slik.PDFReader;
import org.cekpelunasan.platform.whatsapp.dto.webhook.WhatsAppWebhookDTO;
import org.cekpelunasan.platform.whatsapp.service.sender.WhatsAppSenderService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.List;

/**
 * Menangani perintah {@code .slik {nama}} dari WhatsApp.
 * <p>
 * Flow: cari 1 file PDF di R2 berdasarkan nama → ekstrak nomor KTP dari PDF →
 * fetch file metadata TXT → generate 2 PDF (fasilitas aktif + semua) →
 * kirim 3 file ke user: PDF asli, PDF fasilitas aktif, PDF semua fasilitas.
 * </p>
 * <p>
 * Jika generate PDF gagal (KTP tidak ditemukan, file TXT tidak ada, dsb),
 * hanya PDF asli yang dikirimkan sebagai fallback.
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SlikService {

    private static final String COMMAND = ".slik ";

    @Value("${r2.bucket}")
    private String bucket;

    private final S3AsyncClient s3AsyncClient;
    private final S3ClientConfiguration s3Client;
    private final GeneratePdfFiles generatePdfFiles;
    private final PDFReader pdfReader;
    private final WhatsAppSenderService senderService;

    /**
     * Entry point untuk perintah {@code .slik {nama}}.
     * Validasi input → cari file → proses reaktif → kirim file.
     *
     * @param webhookDTO data pesan yang masuk dari WhatsApp
     */
    public void handleSlikService(WhatsAppWebhookDTO webhookDTO) {
        String body = webhookDTO.getPayload().getBody();
        String chatId = webhookDTO.buildChatId();

        if (body.length() <= COMMAND.length()) {
            senderService.sendWhatsAppText(chatId, "Format: .slik {nama nasabah}").subscribe();
            return;
        }

        String nama = body.substring(COMMAND.length()).trim();
        if (nama.isBlank()) {
            senderService.sendWhatsAppText(chatId, "Format: .slik {nama nasabah}").subscribe();
            return;
        }

        String fileName = getMatchingItems(nama, getBucketList());
        if (fileName == null) {
            senderService.sendWhatsAppText(chatId, "❌ Data tidak ditemukan untuk: *" + nama + "*").subscribe();
            return;
        }

        log.info("SLIK match: {} for query: {}", fileName, nama);
        senderService.sendWhatsAppText(chatId, "⏳ Data ditemukan, sedang memproses 3 file PDF...").subscribe();

        String displayName = extractDisplayName(fileName);

        s3Client.getFile(fileName)
            .flatMap(pdfBytes ->
                buildGeneratedPdfs(pdfBytes)
                    .flatMap(pdfs ->
                        senderService.sendWhatsAppFile(chatId, pdfBytes,
                                "SLIK_Asli_" + displayName + ".pdf", "📄 1/3 — SLIK Asli")
                            .then(senderService.sendWhatsAppFile(chatId, pdfs.getT1(),
                                "SLIK_Aktif_" + displayName + ".pdf", "✅ 2/3 — Fasilitas Aktif"))
                            .then(senderService.sendWhatsAppFile(chatId, pdfs.getT2(),
                                "SLIK_Semua_" + displayName + ".pdf", "📋 3/3 — Semua Fasilitas"))
                            .then()
                    )
                    .onErrorResume(e -> {
                        log.error("SLIK: generate/send failed for {}: {}", fileName, e.getMessage());
                        return senderService.sendWhatsAppFile(chatId, pdfBytes,
                            "SLIK_Asli_" + displayName + ".pdf",
                            "📄 SLIK Asli (generate PDF tidak tersedia)").then();
                    })
            )
            .onErrorResume(e -> {
                log.error("SLIK: fetch from S3 failed for {}: {}", fileName, e.getMessage());
                return senderService.sendWhatsAppText(chatId, "❌ Gagal mengambil file dari server.").then();
            })
            .subscribe(
                v -> log.info("SLIK flow completed: {} → {}", fileName, chatId),
                e -> log.error("SLIK unhandled error: {}", e.getMessage())
            );
    }

    /**
     * Mengekstrak nomor KTP dari PDF, fetch file TXT-nya, lalu generate 2 PDF secara paralel.
     * Setiap step yang mengembalikan empty dikonversi ke error agar fallback di caller terpicu.
     */
    private Mono<reactor.util.function.Tuple2<byte[], byte[]>> buildGeneratedPdfs(byte[] pdfBytes) {
        return pdfReader.generateIDNumber(pdfBytes)
            .switchIfEmpty(Mono.error(new IllegalStateException("Nomor KTP tidak ditemukan di PDF")))
            .flatMap(ktp -> s3Client.getFile("KTP_" + ktp + ".txt"))
            .switchIfEmpty(Mono.error(new IllegalStateException("File metadata KTP tidak ditemukan di S3")))
            .flatMap(txt -> Mono.zip(
                generatePdfFiles.generatePdf(txt, true)
                    .switchIfEmpty(Mono.error(new IllegalStateException("Generate PDF fasilitas aktif gagal"))),
                generatePdfFiles.generatePdf(txt, false)
                    .switchIfEmpty(Mono.error(new IllegalStateException("Generate PDF semua fasilitas gagal")))
            ));
    }

    /**
     * Mengekstrak nama tampilan dari key file S3.
     * Contoh: "SMG_2024_budi_santoso.pdf" → "Budi_Santoso"
     */
    private String extractDisplayName(String fileName) {
        String withoutExt = fileName.replaceAll("\\.pdf$", "");
        String[] parts = withoutExt.split("_");
        if (parts.length > 2) {
            StringBuilder sb = new StringBuilder();
            for (int i = 2; i < parts.length; i++) {
                if (i > 2) sb.append("_");
                String part = parts[i];
                if (!part.isEmpty()) {
                    sb.append(Character.toUpperCase(part.charAt(0)));
                    if (part.length() > 1) sb.append(part.substring(1));
                }
            }
            return sb.toString();
        }
        return withoutExt;
    }

    /**
     * Mengambil semua nama file di bucket R2, mengecualikan file KTP metadata (prefix "KTP_").
     *
     * @return daftar nama file, atau list kosong jika gagal
     */
    public List<String> getBucketList() {
        try {
            ListObjectsV2Request request = ListObjectsV2Request.builder().bucket(bucket).build();
            ListObjectsV2Response response = s3AsyncClient.listObjectsV2(request).get();
            return response.contents().stream().map(S3Object::key).toList();
        } catch (Exception e) {
            log.error("Error listing S3 bucket: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Mencari nama file pertama yang mengandung kata kunci (case-insensitive),
     * mengabaikan file metadata KTP.
     *
     * @param name  kata kunci pencarian
     * @param items daftar nama file yang akan dicari
     * @return nama file pertama yang cocok, atau {@code null} jika tidak ada
     */
    public String getMatchingItems(String name, List<String> items) {
        return items.stream()
            .filter(item -> !item.startsWith("KTP_"))
            .filter(item -> item.toLowerCase().contains(name.toLowerCase()))
            .findFirst()
            .orElse(null);
    }
}
