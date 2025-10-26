package org.cekpelunasan.configuration;


import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/*
Using interceptor to intercept all requests to the /webhook endpoint.
Also intercepts all requests to the /whatsapp endpoint.
 */

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
			.addPathPatterns("/v2/whatsapp");
	}


}
