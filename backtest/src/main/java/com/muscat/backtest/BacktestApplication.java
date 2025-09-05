package com.muscat.backtest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class BacktestApplication {

  public static void main(String[] args) {
    SpringApplication.run(BacktestApplication.class, args);
  }

}
