package com.geupjido;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GeupjidoBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(GeupjidoBackendApplication.class, args);
	}

}
