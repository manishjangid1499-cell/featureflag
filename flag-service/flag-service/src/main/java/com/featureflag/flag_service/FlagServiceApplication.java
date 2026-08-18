package com.featureflag.flag_service;

import com.featureflag.flag_service.security.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
@EnableConfigurationProperties(JwtProperties.class)

public class FlagServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(FlagServiceApplication.class, args);
	}
}
