package org.cekpelunasan.aspect

import it.tdlight.client.SimpleTelegramClient
import it.tdlight.jni.TdApi
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.cekpelunasan.annotation.RequireAuth
import org.cekpelunasan.core.entity.AccountOfficerRoles
import org.cekpelunasan.core.service.auth.AuthorizedChats
import org.cekpelunasan.platform.telegram.service.TelegramMessageService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/**
 * Interceptor yang memvalidasi hak akses pengguna sebelum method handler bot dieksekusi.
 *
 * Bekerja sebagai AOP "around advice" — setiap kali ada method yang dianotasi dengan
 * [RequireAuth] dipanggil, aspek ini mengambil alih eksekusi, memeriksa apakah
 * pengirim pesan punya izin yang diperlukan, baru kemudian memutuskan apakah melanjutkan
 * atau menolak dengan mengirim pesan error ke pengguna.
 *
 * Alur pengecekan:
 * 1. Ambil `chatId` dari argumen [TdApi.UpdateNewMessage] yang diterima method
 * 2. Cek apakah chat tersebut terdaftar sebagai pengguna terotorisasi
 * 3. Jika role-nya ADMIN, langsung lanjutkan
 * 4. Jika bukan admin, periksa apakah role pengguna ada di daftar role yang diizinkan anotasi
 * 5. Tolak jika tidak memenuhi syarat
 */
@Aspect
@Component
class AuthorizationAspect(
    private val authorizedChats: AuthorizedChats,
    private val telegramMessageService: TelegramMessageService,
) {

    companion object {
        private val log = LoggerFactory.getLogger(AuthorizationAspect::class.java)
    }

    @Around("@annotation(requireAuth)")
    @Throws(Throwable::class)
    fun checkAuth(joinPoint: ProceedingJoinPoint, requireAuth: RequireAuth): Any? {
        val args = joinPoint.args
        val update = args.filterIsInstance<TdApi.UpdateNewMessage>().firstOrNull()
        val client = args.filterIsInstance<SimpleTelegramClient>().firstOrNull()

        if (update == null || client == null) {
            return joinPoint.proceed()
        }

        val chatId = update.message.chatId

        if (!authorizedChats.isAuthorized(chatId)) {
            log.info("Akses ditolak untuk chatId={}", chatId)
            telegramMessageService.sendText(chatId, "Anda tidak memiliki akses ke bot ini", client)
            return Mono.empty<Any>()
        }

        val roles = authorizedChats.getUserRoles(chatId)
            .subscribeOn(Schedulers.boundedElastic())
            .block()

        if (roles == AccountOfficerRoles.ADMIN) {
            return joinPoint.proceed()
        }

        if (requireAuth.roles.none { it == roles }) {
            log.info("Role tidak cukup untuk chatId={}, role={}", chatId, roles)
            telegramMessageService.sendText(chatId, "Anda tidak memiliki akses ke bot ini", client)
            return Mono.empty<Any>()
        }

        return joinPoint.proceed()
    }
}
