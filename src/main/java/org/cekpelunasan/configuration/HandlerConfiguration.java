package org.cekpelunasan.configuration;


import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration class for Web MVC handlers and interceptors.
 * <p>
 * This class registers interceptors to pre-process requests for specific endpoints,
 * such as /webhook and /v2/whatsapp.
 * </p>
 */
@Configuration
public class HandlerConfiguration implements WebMvcConfigurer {

	private final WebhookInterceptor webhookInterceptor;

	public HandlerConfiguration(WebhookInterceptor webhookInterceptor) {
		this.webhookInterceptor = webhookInterceptor;
	}

	/**
	 * Registers interceptors to the registry.
	 *
	 * @param registry The {@link InterceptorRegistry} to add interceptors to.
	 */
	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(webhookInterceptor)
			.addPathPatterns("/webhook")
			.addPathPatterns("/v2/whatsapp");
	}


}
