package org.cekpelunasan.miniapp.auth;

import org.cekpelunasan.core.entity.AccountOfficerRoles;

import java.time.Instant;

/**
 * Sesi aktif pengguna Mini App setelah berhasil verifikasi initData Telegram.
 */
public record MiniAppSession(
        String token,
        Long chatId,
        AccountOfficerRoles roles,
        Instant expiresAt
) {}
