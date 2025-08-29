package com.muscat.trade.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "dividend_history")
@Getter
@Setter
@NoArgsConstructor
public class DividendHistory {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  @Column(nullable = false)
  private String userId;

  @Column(nullable = false)
  private String accountId;

  @Column(nullable = false)
  private String symbol;

  @Column(nullable = false, precision = 19, scale = 8)
  private BigDecimal quantity;

  @Column(nullable = false, precision = 19, scale = 8)
  private BigDecimal dividendPerShare;

  @Column(nullable = false, precision = 19, scale = 8)
  private BigDecimal totalDividend;

  @Column(nullable = false)
  private LocalDate dividendDate;

  @Column(nullable = false)
  private LocalDate recordDate;

  @Column(nullable = false)
  private LocalDateTime processedAt;

  public DividendHistory(String userId, String accountId, String symbol, 
                        BigDecimal quantity, BigDecimal dividendPerShare, 
                        BigDecimal totalDividend, LocalDate dividendDate, LocalDate recordDate) {
    this.userId = userId;
    this.accountId = accountId;
    this.symbol = symbol;
    this.quantity = quantity;
    this.dividendPerShare = dividendPerShare;
    this.totalDividend = totalDividend;
    this.dividendDate = dividendDate;
    this.recordDate = recordDate;
    this.processedAt = LocalDateTime.now();
  }
}