package org.cekpelunasan.configuration;


import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;

@Configuration
public class RequestWrapperFilter implements Filter {
	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
		ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper((HttpServletRequest) servletRequest);

		filterChain.doFilter(wrappedRequest, servletResponse);
	}
}
