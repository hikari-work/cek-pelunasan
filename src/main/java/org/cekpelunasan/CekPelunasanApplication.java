package org.cekpelunasan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for Cek Pelunasan service.
 * <p>
 * This class serves as the entry point for the Spring Boot application.
 * It enables JPA repositories, entity scanning, asynchronous processing, and scheduling.
 * </p>
 */
@SpringBootApplication
@EnableJpaRepositories("org.cekpelunasan.repository")
@EntityScan("org.cekpelunasan.entity")
@EnableAsync
@EnableScheduling
public class CekPelunasanApplication {

	/**
	 * Main method to start the Spring Boot application.
	 *
	 * @param args Command line arguments passed to the application.
	 */
	public static void main(String[] args) {
		SpringApplication.run(CekPelunasanApplication.class, args);
	}

}
