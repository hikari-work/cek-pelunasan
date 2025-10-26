package org.cekpelunasan.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
@EnableAsync
public class AsyncConfiguration implements AsyncConfigurer {

	/*
	This project mostly do I/O bound tasks, so using virtual threads is more efficient.
	Using virtual threads allows the application to handle a large number of concurrent tasks
	with minimal resource consumption, as virtual threads are lightweight and managed by the JVM.
	 */
	@Override
	public Executor getAsyncExecutor() {
		return Executors.newVirtualThreadPerTaskExecutor();
	}

}
