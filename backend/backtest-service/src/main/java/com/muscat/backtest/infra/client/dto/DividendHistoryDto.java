package com.muscat.backtest.infra.client.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
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

    public static DividendPayment from(DividendDto dto) {
      DividendPayment payment = new DividendPayment();
      payment.setExDate(dto.exDate());
      payment.setPayDate(dto.paymentDate());
      payment.setAmount(dto.amount());
      payment.setFrequency(null);
      return payment;
    }
  }

  public static DividendHistoryDto of(String symbol, List<DividendDto> dividendList) {
    DividendHistoryDto history = new DividendHistoryDto();
    history.setSymbol(symbol);

    if (dividendList == null || dividendList.isEmpty()) {
      history.setDividends(Collections.emptyList());
      return history;
    }

    history.setDividends(dividendList.stream()
        .map(DividendPayment::from)
        .toList());

    return history;
  }
}
