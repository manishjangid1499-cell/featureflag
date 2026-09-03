package com.featureflag.auth_service;

import com.featureflag.auth_service.config.OwnerBootstrapProperties;
import com.featureflag.auth_service.config.JwtProperties;
import com.featureflag.auth_service.config.LoginRateLimitProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@SpringBootApplication
@EnableConfigurationProperties({
        OwnerBootstrapProperties.class,
        JwtProperties.class,
        LoginRateLimitProperties.class
})
public class AuthServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AuthServiceApplication.class, args);
	}

}
