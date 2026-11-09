package com.muscat.trade.domain.entity;

import com.muscat.trade.domain.service.ProfitFlowCalculator.DailyProfit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 계좌 · 거래일마다 수익 흐름 계산 결과(USD 금액은 평가손익 · 투자 원금만 그 날짜 값이고 나머지는 누적)
@Entity
@Table(name = "account_profit_daily",
  uniqueConstraints = @UniqueConstraint(name = "uk_account_profit_daily", columnNames = {"accountId", "tradeDate"}),
  indexes = @Index(name = "idx_account_profit_daily_user", columnList = "userId"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountProfitDaily {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String userId;

  @Column(nullable = false)
  private Long accountId;

  @Column(nullable = false)
  private LocalDate tradeDate;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal unrealizedUsd;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal realizedUsd;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal dividendUsd;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal feeUsd;

  @Column(nullable = false, precision = 18, scale = 6)
  private BigDecimal fxRate;

  @Column(nullable = false, precision = 19, scale = 0)
  private BigDecimal profitKrw;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal investedUsd;

  @Column(nullable = false, precision = 19, scale = 0)
  private BigDecimal investedKrw;

  @Column(nullable = false)
  private LocalDateTime computedAt;

  public static AccountProfitDaily of(String userId, Long accountId, DailyProfit day, LocalDateTime computedAt) {
    AccountProfitDaily row = new AccountProfitDaily();
    row.userId = userId;
    row.accountId = accountId;
    row.tradeDate = day.date();
    row.unrealizedUsd = day.unrealizedUsd();
    row.realizedUsd = day.realizedUsd();
    row.dividendUsd = day.dividendUsd();
    row.feeUsd = day.feeUsd();
    row.fxRate = day.fxRate();
    row.profitKrw = day.profitKrw();
    row.investedUsd = day.investedUsd();
    row.investedKrw = day.investedKrw();
    row.computedAt = computedAt;
    return row;
  }
}
