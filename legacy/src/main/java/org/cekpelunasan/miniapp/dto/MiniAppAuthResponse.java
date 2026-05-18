package org.cekpelunasan.miniapp.dto;

/**
 * Response dari endpoint POST /api/mini/auth berisi session token dan info pengguna.
 */
public record MiniAppAuthResponse(String token, UserInfoDTO user) {}
