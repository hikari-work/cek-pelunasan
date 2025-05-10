package org.cekpelunasan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableJpaRepositories("org.cekpelunasan.repository")
@EntityScan("org.cekpelunasan.entity")
@EnableAsync
public class CekPelunasanApplication {

	public static void main(String[] args) {
		SpringApplication.run(CekPelunasanApplication.class, args);
	}

}
