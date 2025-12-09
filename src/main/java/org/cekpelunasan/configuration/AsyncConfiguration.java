package org.cekpelunasan.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Configuration class for asynchronous processing.
 * <p>
 * This class enables Spring's asynchronous method execution capability.
 * It configures a virtual thread-based executor to handle tasks efficiently, especially for I/O-bound operations.
 * </p>
 */
@Configuration
@EnableAsync
public class AsyncConfiguration implements AsyncConfigurer {

	/*
	This project mostly do I/O bound tasks, so using virtual threads is more efficient.
	Using virtual threads allows the application to handle a large number of concurrent tasks
	with minimal resource consumption, as virtual threads are lightweight and managed by the JVM.
	 */
	/**
	 * Configures the executor to be used for asynchronous methods.
	 *
	 * @return An {@link Executor} that uses virtual threads for each task.
	 */
	@Override
	public Executor getAsyncExecutor() {
		return Executors.newVirtualThreadPerTaskExecutor();
	}

}
