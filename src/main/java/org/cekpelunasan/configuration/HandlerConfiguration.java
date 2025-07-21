package org.cekpelunasan.configuration;


import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class HandlerConfiguration implements WebMvcConfigurer {

	private final WebhookInterceptor webhookInterceptor;

	public HandlerConfiguration(WebhookInterceptor webhookInterceptor) {
		this.webhookInterceptor = webhookInterceptor;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(webhookInterceptor)
			.addPathPatterns("/webhook")
			.addPathPatterns("/whatsapp");
	}


}
