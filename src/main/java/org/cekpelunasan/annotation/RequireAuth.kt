package org.cekpelunasan.annotation

import org.cekpelunasan.core.entity.AccountOfficerRoles

/**
 * Anotasi untuk melindungi method handler bot agar hanya bisa diakses oleh pengguna
 * yang sudah terotorisasi dan memiliki role yang sesuai.
 * 
 * 
 * Pasang anotasi ini di atas method handler Telegram atau WhatsApp, lalu tentukan
 * role apa saja yang boleh mengaksesnya. Pengecekan dilakukan secara otomatis oleh
 * `AuthorizationAspect` saat method dipanggil — kode handler tidak perlu
 * mengurus validasi itu sendiri.
 * 
 * 
 * 
 * Contoh pemakaian:
 * 
 * <pre>
 * @RequireAuth(roles = {AccountOfficerRoles.ADMIN, AccountOfficerRoles.SUPERVISOR})
 * public void handleLaporanBulanan(UpdateNewMessage update, SimpleTelegramClient client) {
 * // hanya ADMIN dan SUPERVISOR yang sampai ke sini
 * }
</pre> * 
 * 
 * 
 * Jika `roles` dibiarkan kosong, aspek hanya memastikan pengguna sudah terdaftar
 * sebagai chat yang diotorisasi, tanpa mempermasalahkan role-nya.
 * 
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class RequireAuth(
    /**
     * Daftar role yang diperbolehkan mengakses method ini.
     * 
     * 
     * Pengguna dengan role `ADMIN` selalu lolos terlepas dari isi array ini.
     * Jika array kosong, cukup menjadi pengguna yang terotorisasi saja.
     * 
     * 
     * @return array role yang diizinkan, default-nya kosong (semua role terotorisasi boleh akses)
     */
    val roles: Array<AccountOfficerRoles> = []
)
