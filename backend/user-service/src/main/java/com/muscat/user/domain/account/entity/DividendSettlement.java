package com.muscat.user.domain.account.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 계좌 · 종목 · 배당락일마다 지금까지 넣은 배당 금액(한 행이라 같은 지급이 다시 와도 두 번 넣지 않음)
@Entity
@Table(name = "dividend_settlements", uniqueConstraints = @UniqueConstraint(
  name = "uk_dividend_settlements", columnNames = {"account_id", "symbol", "ex_date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DividendSettlement {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "account_id", nullable = false)
  private Long accountId;

  @Column(name = "symbol", nullable = false, length = 20)
  private String symbol;

  @Column(name = "ex_date", nullable = false)
  private LocalDate exDate;

  @Column(nullable = false, precision = 15, scale = 4)
  private BigDecimal amount;

  @Column(nullable = false)
  private LocalDateTime processedAt;

  public static DividendSettlement of(Long accountId, String symbol, LocalDate exDate, BigDecimal amount,
    LocalDateTime processedAt) {
    DividendSettlement settlement = new DividendSettlement();
    settlement.accountId = accountId;
    settlement.symbol = symbol;
    settlement.exDate = exDate;
    settlement.amount = amount;
    settlement.processedAt = processedAt;
    return settlement;
  }

  public void changeAmount(BigDecimal amount, LocalDateTime processedAt) {
    this.amount = amount;
    this.processedAt = processedAt;
  }
}
