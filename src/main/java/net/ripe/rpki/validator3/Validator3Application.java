package net.ripe.rpki.validator3;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Validator3Application {

	public static void main(String[] args) {
		SpringApplication.run(Validator3Application.class, args);
	}
}
