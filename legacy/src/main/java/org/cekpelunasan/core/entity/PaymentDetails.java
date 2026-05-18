package org.cekpelunasan.core.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Data detail pembayaran angsuran nasabah per AO, bersumber dari file CSV DB2.
 * <p>
 * Setiap record merepresentasikan satu baris posting angsuran. Kode posting
 * membedakan antara angsuran pokok (P) dan bunga (I).
 * </p>
 * <p>
 * Unique key dokumen ini dibentuk dari hash seluruh kolom data sehingga
 * upload ulang baris yang identik dari DB2 tidak menghasilkan duplikasi.
 * </p>
 * <p>
 * Koleksi MongoDB yang dipakai adalah {@code payment_details}.
 * </p>
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collection = "payment_details")
public class PaymentDetails {

    /**
     * ID unik dokumen, dibentuk dari kombinasi seluruh kolom data.
     * Format: {@code tanggal|kodeCabang|kodeAo|noSpk|nama|kodePosting|nominalAngsuran|denda|penalti}.
     * Menjamin tidak ada duplikasi meski data diupload berulang kali dari DB2.
     */
    @Id
    private String id;

    /**
     * Tanggal transaksi dalam format {@code yyyyMMdd}, misalnya "20250102".
     * Bersumber dari kolom {@code MLTPODT} di CSV.
     */
    private String tanggal;

    /**
     * Kode cabang yang mengelola nasabah ini, misalnya "1014".
     * Bersumber dari kolom {@code MDLBRCO} di CSV.
     */
    private String kodeCabang;

    /**
     * Kode Account Officer yang bertanggung jawab atas nasabah ini, misalnya "SGI".
     * Bersumber dari kolom {@code MDLAOCO} di CSV.
     */
    private String kodeAo;

    /**
     * Nomor SPK (Surat Perintah Kerja) sebagai identitas unik kredit nasabah.
     * Bersumber dari kolom {@code MDLDLRF} di CSV.
     */
    private String noSpk;

    /**
     * Nama lengkap nasabah.
     * Bersumber dari kolom {@code MDLNAME} di CSV.
     */
    private String nama;

    /**
     * Kode posting angsuran: {@code P} untuk Pokok, {@code I} untuk Bunga.
     * Bersumber dari kolom {@code MLTSCTY} di CSV.
     */
    private String kodePosting;

    /**
     * Total nominal angsuran yang masuk, dalam rupiah.
     * Bersumber dari kolom {@code MLTAMNT} di CSV.
     */
    private Long nominalAngsuran;

    /**
     * Nominal denda keterlambatan, dalam rupiah.
     * Bersumber dari kolom {@code MLTAMPE} di CSV.
     */
    private Long denda;

    /**
     * Nominal penalti pelunasan dipercepat, dalam rupiah.
     * Bersumber dari kolom {@code MLTWAME} di CSV.
     */
    private Long penalti;

    /**
     * Flag yang menandai apakah record ini adalah transaksi pelunasan.
     * Bernilai {@code true} jika {@code denda} atau {@code penalti} lebih dari nol,
     * yang berarti {@code nominalAngsuran} mencakup komponen denda/penalti pelunasan.
     */
    private boolean flagPelunasan;
}
