package com.muscat.backtest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableFeignClients
@EnableCaching
@ComponentScan(basePackages = {"com.muscat.backtest", "com.muscat.commonlib"})
public class BacktestApplication {

  public static void main(String[] args) {
    SpringApplication.run(BacktestApplication.class, args);
  }

}
