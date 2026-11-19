package com.muscat.trade.domain.entity;

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

// 체결에 반영한 주식 분할(종목 · 분할일마다 한 줄)
@Entity
@Table(name = "stock_splits",
  uniqueConstraints = @UniqueConstraint(name = "uk_stock_splits", columnNames = {"symbol", "splitDate"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockSplit {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 16)
  private String symbol;

  @Column(nullable = false)
  private LocalDate splitDate;

  // 분할 전 1주가 분할 뒤 몇 주인지
  @Column(nullable = false, precision = 19, scale = 8)
  private BigDecimal ratio;

  // 분할로 수량 · 체결가를 맞춘 체결 수
  @Column(nullable = false)
  private int adjustedTrades;

  @Column(nullable = false)
  private LocalDateTime appliedAt;

  public static StockSplit of(String symbol, LocalDate splitDate, BigDecimal ratio, int adjustedTrades,
                              LocalDateTime appliedAt) {
    StockSplit split = new StockSplit();
    split.symbol = symbol;
    split.splitDate = splitDate;
    split.ratio = ratio;
    split.adjustedTrades = adjustedTrades;
    split.appliedAt = appliedAt;
    return split;
  }

  // 더 맞춘 체결 수와 반영 시각
  public void addAdjusted(int count, LocalDateTime at) {
    this.adjustedTrades += count;
    this.appliedAt = at;
  }
}
