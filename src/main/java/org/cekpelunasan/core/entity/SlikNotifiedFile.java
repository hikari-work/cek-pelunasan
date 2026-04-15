package org.cekpelunasan.core.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Menyimpan nama file SLIK yang sudah pernah dinotifikasi ke pengguna.
 *
 * <p>Digunakan oleh {@code SendNotificationSlikUpdated} untuk memastikan
 * setiap file hanya dinotifikasi sekali, meski aplikasi di-restart.
 * Alternatif dari S3 object tagging yang tidak didukung oleh Cloudflare R2.</p>
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collection = "slik_notified_files")
public class SlikNotifiedFile {

    /**
     * Key/nama file di bucket R2 — sekaligus primary key dokumen.
     */
    @Id
    private String fileKey;

    /**
     * Waktu notifikasi dikirim, dalam zona waktu UTC+7 (WIB).
     */
    @Indexed
    private LocalDateTime notifiedAt;
}
