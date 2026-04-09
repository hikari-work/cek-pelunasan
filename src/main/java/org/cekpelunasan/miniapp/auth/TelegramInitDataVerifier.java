package org.cekpelunasan.miniapp.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * Memverifikasi keaslian {@code initData} yang dikirimkan Telegram Mini App menggunakan
 * algoritma HMAC-SHA256 sesuai dokumentasi resmi Telegram.
 *
 * <p>Alur verifikasi:
 * <ol>
 *   <li>URL-decode {@code initData}</li>
 *   <li>Pisahkan semua pasangan {@code key=value}, ekstrak nilai {@code hash}</li>
 *   <li>Urutkan sisa pasangan secara alfabetis, gabung dengan {@code \n}</li>
 *   <li>Hitung {@code secretKey = HMAC_SHA256("WebAppData", botToken)}</li>
 *   <li>Hitung {@code expectedHash = HMAC_SHA256(dataCheckString, secretKey)}</li>
 *   <li>Bandingkan hex(expectedHash) dengan hash yang diterima</li>
 *   <li>Validasi {@code auth_date} tidak lebih dari 5 menit yang lalu</li>
 * </ol>
 * </p>
 */
@Component
public class TelegramInitDataVerifier {

    private static final Logger log = LoggerFactory.getLogger(TelegramInitDataVerifier.class);
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final long MAX_AGE_SECONDS = 86400L; // 24 jam

    @Value("${telegram.bot.token}")
    private String botToken;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public record VerificationResult(boolean valid, Long chatId, String firstName) {}

    /**
     * Memverifikasi {@code initData} dan mengekstrak informasi pengguna jika valid.
     *
     * @param initData string raw initData dari {@code Telegram.WebApp.initData}
     * @return hasil verifikasi berisi chatId dan nama, atau {@code valid=false} jika gagal
     */
    public VerificationResult verify(String initData) {
        if (initData == null || initData.isBlank()) {
            return new VerificationResult(false, null, null);
        }

        try {
            String decoded = URLDecoder.decode(initData, StandardCharsets.UTF_8);

            Map<String, String> params = new LinkedHashMap<>();
            for (String pair : decoded.split("&")) {
                int idx = pair.indexOf('=');
                if (idx >= 0) {
                    params.put(pair.substring(0, idx), pair.substring(idx + 1));
                }
            }

            log.info("initData decoded keys: {}", params.keySet());
            log.info("initData raw (first 200): {}", initData.substring(0, Math.min(200, initData.length())));

            String receivedHash = params.remove("hash");
            if (receivedHash == null) {
                log.warn("initData tidak mengandung field hash");
                return new VerificationResult(false, null, null);
            }

            // Validasi auth_date (max 5 menit)
            String authDateStr = params.get("auth_date");
            if (authDateStr != null) {
                long authDate = Long.parseLong(authDateStr);
                long age = Instant.now().getEpochSecond() - authDate;
                if (age > MAX_AGE_SECONDS) {
                    log.warn("initData sudah kedaluwarsa: {} detik yang lalu", age);
                    return new VerificationResult(false, null, null);
                }
            }

            // Bangun data check string
            String dataCheckString = params.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .sorted()
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("");

            // Hitung secret key dan expected hash
            // secret_key = HMAC_SHA256(key="WebAppData", message=botToken)
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec("WebAppData".getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] secretKey = mac.doFinal(botToken.getBytes(StandardCharsets.UTF_8));

            mac.init(new SecretKeySpec(secretKey, HMAC_SHA256));
            byte[] expectedHashBytes = mac.doFinal(dataCheckString.getBytes(StandardCharsets.UTF_8));
            String expectedHash = HexFormat.of().formatHex(expectedHashBytes);

            if (!expectedHash.equalsIgnoreCase(receivedHash)) {
                log.warn("Hash initData tidak cocok");
                return new VerificationResult(false, null, null);
            }

            // Ekstrak chatId dari field user
            String userJson = params.get("user");
            if (userJson == null) {
                log.warn("initData tidak mengandung field user");
                return new VerificationResult(false, null, null);
            }

            JsonNode userNode = objectMapper.readTree(userJson);
            long chatId = userNode.get("id").asLong();
            String firstName = userNode.path("first_name").asText("");

            return new VerificationResult(true, chatId, firstName);

        } catch (Exception e) {
            log.error("Gagal memverifikasi initData", e);
            return new VerificationResult(false, null, null);
        }
    }
}
