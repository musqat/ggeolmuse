package com.muscat.backtest.infra.client.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;

@Data
public class DividendHistoryDto {

  private String symbol;
  private List<DividendPayment> dividends;

  @Data
  public static class DividendPayment {

    private LocalDate exDate; // 배당락일
    private LocalDate payDate; // 배당지급일
    private BigDecimal amount; // 배당금액 (USD)
    private String frequency; // 배당주기 (QUARTERLY, ANNUAL 등)
  }
}
