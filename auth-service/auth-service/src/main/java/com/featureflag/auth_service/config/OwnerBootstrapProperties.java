package com.featureflag.auth_service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.bootstrap.owner")
public class OwnerBootstrapProperties {

    private boolean enabled;

    private String name;

    private String email;

    private String password;
}