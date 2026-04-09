package org.cekpelunasan.miniapp.dto;

import org.cekpelunasan.core.entity.AccountOfficerRoles;

/**
 * Informasi pengguna yang dikembalikan bersama token setelah autentikasi berhasil.
 */
public record UserInfoDTO(Long chatId, String firstName, AccountOfficerRoles roles) {}
