package com.muscat.user;

import com.muscat.user.config.AppProperties;
import com.muscat.commonlib.config.KeycloakProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableFeignClients
@EnableConfigurationProperties({KeycloakProperties.class, AppProperties.class})
@ComponentScan(basePackages = {"com.muscat.user", "com.muscat.commonlib"})
public class UserApplication {

	public static void main(String[] args) {
		SpringApplication.run(UserApplication.class, args);
	}

}
