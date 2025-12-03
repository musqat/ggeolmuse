package com.muscat.trade.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "dividends", indexes = {
    @Index(name = "idx_dividends_user_symbol_date", columnList = "userId, symbol, dividendDate"),
    @Index(name = "idx_dividends_user_created", columnList = "userId, createdAt"),
    @Index(name = "idx_dividends_symbol_date", columnList = "symbol, dividendDate"),
    @Index(name = "idx_dividends_trade", columnList = "tradeId"),
    @Index(name = "idx_dividends_trade_date", columnList = "tradeId, dividendDate")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dividend {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id; // PK

  @Column(nullable = false)
  private String userId; // 사용자 ID (Keycloak UUID)

  @Column(nullable = false)
  private Long accountId; // 계좌 ID

  @Column(nullable = false, length = 10)
  private String symbol; // 주식 심볼 (AAPL, MSFT)

  @Column
  private Long tradeId; // 연결된 매수 거래 ID (어느 매수에서 발생한 배당인지)

  @Column(nullable = false, precision = 15, scale = 6)
  private BigDecimal shares; // 배당 받은 주식 수량

  @Column(nullable = false, precision = 15, scale = 6)
  private BigDecimal dividendPerShare; // 주당 배당금 (USD)

  @Column(nullable = false, precision = 15, scale = 2)
  private BigDecimal grossAmount; // 원배당금 (세전)

  @Column(nullable = false, precision = 15, scale = 2)
  @Builder.Default
  private BigDecimal taxAmount = BigDecimal.ZERO; // 원천징수세 (15.4%)

  @Column(nullable = false, precision = 15, scale = 2)
  private BigDecimal netAmount; // 실수령액 (세후)

  @Column(nullable = false)
  private LocalDate dividendDate; // 배당 지급일 (ex-date)

  @Column(nullable = false)
  private LocalDateTime processedAt; // 배당 처리 시각

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt; // 레코드 생성일시
}
