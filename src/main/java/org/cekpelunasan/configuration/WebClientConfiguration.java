package org.cekpelunasan.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Base64;

@Configuration
public class WebClientConfiguration {

    @Value("${whatsapp.gateway.url}")
    private String baseUrl;

    @Value("${whatsapp.gateway.username}")
    private String username;

    @Value("${whatsapp.gateway.password}")
    private String password;

    @Value("${whatsapp.device.id:}")
    private String deviceId;

    @Bean
    public WebClient whatsappWebClient() {
        String credentials = Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes());
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Basic " + credentials)
                .defaultHeader("Content-Type", "application/json");
        if (deviceId != null && !deviceId.isBlank()) {
            builder.defaultHeader("X-Device-Id", deviceId);
        }
        return builder.build();
    }
}
