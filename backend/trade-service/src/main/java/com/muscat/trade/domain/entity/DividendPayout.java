package com.muscat.trade.domain.entity;

import com.muscat.trade.domain.service.DividendAmounts;
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

// 계좌 · 종목 · 배당락일마다 받을 배당 몫
@Entity
@Table(name = "dividend_payouts",
  uniqueConstraints = @UniqueConstraint(name = "uk_dividend_payouts", columnNames = {"accountId", "symbol", "exDate"}),
  indexes = @Index(name = "idx_dividend_payouts_user", columnList = "userId"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DividendPayout {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String userId;

  @Column(nullable = false)
  private Long accountId;

  @Column(nullable = false, length = 10)
  private String symbol;

  @Column(nullable = false)
  private LocalDate exDate;

  @Column(nullable = false, precision = 19, scale = 6)
  private BigDecimal shares;

  @Column(nullable = false, precision = 19, scale = 6)
  private BigDecimal dividendPerShare;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal grossAmount;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal taxAmount;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal netAmount;

  @Column(nullable = false)
  private LocalDateTime updatedAt;

  public static DividendPayout of(String userId, Long accountId, String symbol, LocalDate exDate,
    DividendAmounts amounts, LocalDateTime updatedAt) {
    DividendPayout payout = new DividendPayout();
    payout.userId = userId;
    payout.accountId = accountId;
    payout.symbol = symbol;
    payout.exDate = exDate;
    payout.apply(amounts, updatedAt);
    return payout;
  }

  public void apply(DividendAmounts amounts, LocalDateTime updatedAt) {
    this.shares = amounts.shares();
    this.dividendPerShare = amounts.dividendPerShare();
    this.grossAmount = amounts.grossAmount();
    this.taxAmount = amounts.taxAmount();
    this.netAmount = amounts.netAmount();
    this.updatedAt = updatedAt;
  }

  public boolean matches(DividendAmounts amounts) {
    return shares.compareTo(amounts.shares()) == 0
      && dividendPerShare.compareTo(amounts.dividendPerShare()) == 0
      && netAmount.compareTo(amounts.netAmount()) == 0;
  }
}
