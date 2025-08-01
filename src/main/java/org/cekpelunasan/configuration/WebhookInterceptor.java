package org.cekpelunasan.configuration;


import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.cekpelunasan.entity.Logging;
import org.cekpelunasan.repository.LoggingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.util.concurrent.CompletableFuture;


@Component
public class WebhookInterceptor implements HandlerInterceptor {

	private static final Logger log = LoggerFactory.getLogger(WebhookInterceptor.class);
	@Autowired
	private LoggingRepository requestLogRepository;

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
		log.info("New Webhook Received");
		if (request instanceof ContentCachingRequestWrapper wrapper) {
			String body = new String(wrapper.getContentAsByteArray(), wrapper.getCharacterEncoding());
			CompletableFuture.runAsync(() -> requestLogRepository.save(Logging.builder()
				.request(body)
				.build()));
		}
	}
}

