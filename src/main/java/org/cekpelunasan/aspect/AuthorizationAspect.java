package org.cekpelunasan.aspect;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.core.service.auth.AuthorizedChats;
import org.cekpelunasan.platform.telegram.service.TelegramMessageService;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

import java.util.Arrays;

/**
 * Interceptor yang memvalidasi hak akses pengguna sebelum method handler bot dieksekusi.
 * <p>
 * Bekerja sebagai AOP "around advice" — setiap kali ada method yang dianotasi dengan
 * {@link RequireAuth} dipanggil, aspek ini mengambil alih eksekusi, memeriksa apakah
 * pengirim pesan punya izin yang diperlukan, baru kemudian memutuskan apakah melanjutkan
 * atau menolak dengan mengirim pesan error ke pengguna.
 * </p>
 * <p>
 * Alur pengecekan yang dilakukan:
 * <ol>
 *   <li>Ambil {@code chatId} dari argumen {@link TdApi.UpdateNewMessage} yang diterima method</li>
 *   <li>Cek apakah chat tersebut terdaftar sebagai pengguna terotorisasi</li>
 *   <li>Jika terotorisasi dan role-nya ADMIN, langsung lanjutkan — admin bisa semua</li>
 *   <li>Jika bukan admin, periksa apakah role pengguna ada di daftar role yang diizinkan anotasi</li>
 *   <li>Tolak jika tidak memenuhi syarat, dengan mengirim pesan pemberitahuan ke pengguna</li>
 * </ol>
 * </p>
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuthorizationAspect {

    private final AuthorizedChats authorizedChats;
    private final TelegramMessageService telegramMessageService;

    /**
     * Mencegat eksekusi method yang dianotasi {@link RequireAuth} dan menjalankan validasi otorisasi.
     * <p>
     * Jika argumen method tidak mengandung {@link TdApi.UpdateNewMessage} atau
     * {@link SimpleTelegramClient}, aspek ini melewatkan pengecekan dan langsung
     * meneruskan eksekusi — asumsinya method tersebut bukan handler bot Telegram
     * dan tidak perlu dicek.
     * </p>
     * <p>
     * Saat pengguna ditolak, method mengembalikan {@code Mono.empty()} agar tidak
     * ada nilai yang dipropagasikan ke pemanggil dan alur reactive tidak terputus secara kasar.
     * </p>
     *
     * @param joinPoint    titik eksekusi yang dicegat, dipakai untuk mengambil argumen dan meneruskan eksekusi
     * @param requireAuth  instans anotasi {@link RequireAuth} yang berisi daftar role yang diizinkan
     * @return hasil eksekusi method asli, atau {@code Mono.empty()} jika pengguna tidak punya akses
     * @throws Throwable jika eksekusi method asli melempar exception
     */
    @Around("@annotation(requireAuth)")
    public Object checkAuth(@NotNull ProceedingJoinPoint joinPoint, RequireAuth requireAuth) throws Throwable {
        Object[] args = joinPoint.getArgs();
        TdApi.UpdateNewMessage update = null;
        SimpleTelegramClient client = null;
        for (Object arg : args) {
            if (arg instanceof TdApi.UpdateNewMessage u) {
                update = u;
            } else if (arg instanceof SimpleTelegramClient c) {
                client = c;
            }
        }
        if (update == null || client == null) {
            return joinPoint.proceed();
        }
        long chatId = update.message.chatId;
        if (!authorizedChats.isAuthorized(chatId)) {
            telegramMessageService.sendText(chatId, "Anda tidak memiliki akses ke bot ini", client);
            return Mono.empty();
        }
        AccountOfficerRoles roles = authorizedChats.getUserRoles(chatId)
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .block();
        if (roles == AccountOfficerRoles.ADMIN) {
            return joinPoint.proceed();
        }
        AccountOfficerRoles[] requiredRoles = requireAuth.roles();
        boolean hasRequiredRoles = Arrays.stream(requiredRoles).anyMatch(role -> role == roles);
        if (!hasRequiredRoles) {
            telegramMessageService.sendText(chatId, "Anda tidak memiliki akses ke bot ini", client);
            return Mono.empty();
        }
        return joinPoint.proceed();
    }
}
