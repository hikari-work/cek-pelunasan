package org.cekpelunasan.configuration;

import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Configuration class for OkHttpClient.
 * <p>
 * This class creates and configures the {@link OkHttpClient} bean used for
 * making HTTP requests.
 * It sets connection timeouts, read/write timeouts, and adds a logging
 * interceptor.
 * </p>
 */
@Configuration
@Slf4j
public class OkHttpConfiguration {

	/**
	 * Creates a configured {@link OkHttpClient} bean.
	 *
	 * @return The configured {@link OkHttpClient} instance.
	 */
	@Bean
	public OkHttpClient okHttpClient() {
		return new OkHttpClient.Builder()
				.connectionPool(new ConnectionPool(10, 5, TimeUnit.MINUTES))
				.connectTimeout(10, TimeUnit.SECONDS)
				.readTimeout(30, TimeUnit.SECONDS)
				.writeTimeout(30, TimeUnit.SECONDS)
				.addInterceptor(chain -> {
					long start = System.currentTimeMillis();
					Request request = chain.request();
					Response response = chain.proceed(request);
					log.info("Time Taken {} ms", System.currentTimeMillis() - start);
					return response;
				})
				.build();
	}
}
