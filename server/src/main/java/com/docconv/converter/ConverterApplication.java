package com.docconv.converter;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan({"com.docconv.converter", "com.docconv.support", "com.docconv.workflow", "com.docconv.ai"})
public class ConverterApplication {

	public static void main(String[] args) {
		// Load .env file if exists
		Dotenv dotenv = Dotenv.configure()
				.ignoreIfMissing()
				.load();

		// Make .env variables available to Spring
		dotenv.entries().forEach(e ->
				System.setProperty(e.getKey(), e.getValue())
		);

		SpringApplication.run(ConverterApplication.class, args);
	}
}
