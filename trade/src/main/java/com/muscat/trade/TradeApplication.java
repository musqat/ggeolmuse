package com.muscat.trade;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableFeignClients
@EnableCaching
@ComponentScan(basePackages = {"com.muscat.trade", "com.muscat.commonlib"})
public class TradeApplication {

  public static void main(String[] args) {
    SpringApplication.run(TradeApplication.class, args);
  }

}
