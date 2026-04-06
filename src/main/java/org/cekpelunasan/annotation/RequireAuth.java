package org.cekpelunasan.annotation;

import org.cekpelunasan.core.entity.AccountOfficerRoles;

import java.lang.annotation.*;

/**
 * Anotasi untuk melindungi method handler bot agar hanya bisa diakses oleh pengguna
 * yang sudah terotorisasi dan memiliki role yang sesuai.
 * <p>
 * Pasang anotasi ini di atas method handler Telegram atau WhatsApp, lalu tentukan
 * role apa saja yang boleh mengaksesnya. Pengecekan dilakukan secara otomatis oleh
 * {@code AuthorizationAspect} saat method dipanggil — kode handler tidak perlu
 * mengurus validasi itu sendiri.
 * </p>
 * <p>
 * Contoh pemakaian:
 * </p>
 * <pre>
 * {@literal @}RequireAuth(roles = {AccountOfficerRoles.ADMIN, AccountOfficerRoles.SUPERVISOR})
 * public void handleLaporanBulanan(UpdateNewMessage update, SimpleTelegramClient client) {
 *     // hanya ADMIN dan SUPERVISOR yang sampai ke sini
 * }
 * </pre>
 * <p>
 * Jika {@code roles} dibiarkan kosong, aspek hanya memastikan pengguna sudah terdaftar
 * sebagai chat yang diotorisasi, tanpa mempermasalahkan role-nya.
 * </p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireAuth {

	/**
	 * Daftar role yang diperbolehkan mengakses method ini.
	 * <p>
	 * Pengguna dengan role {@code ADMIN} selalu lolos terlepas dari isi array ini.
	 * Jika array kosong, cukup menjadi pengguna yang terotorisasi saja.
	 * </p>
	 *
	 * @return array role yang diizinkan, default-nya kosong (semua role terotorisasi boleh akses)
	 */
	AccountOfficerRoles[] roles() default {};
}
