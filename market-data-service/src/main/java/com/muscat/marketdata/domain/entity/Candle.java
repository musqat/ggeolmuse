package com.muscat.marketdata.domain.entity;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "candle",
    uniqueConstraints = @UniqueConstraint(columnNames = {"symbol", "date", "currency"}),
    indexes = {
        @Index(name = "idx_candle_symbol_date", columnList = "symbol,date")
    })
public class Candle {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "symbol", nullable = false, length = 16)
  private String symbol;

  @Column(name = "date", nullable = false)
  private LocalDate date;

  @Column(name = "currency", nullable = false, length = 3)
  private String currency;

  // ===== OHLCV 데이터 =====

  @Column(name = "open", precision = 19, scale = 8)
  private BigDecimal open;

  @Column(name = "high", precision = 19, scale = 8)
  private BigDecimal high;

  @Column(name = "low", precision = 19, scale = 8)
  private BigDecimal low;

  @Column(name = "close", precision = 19, scale = 8)
  private BigDecimal close;

  @Column(name = "volume")
  private Long volume;

  // ===== 백테스트용 보정 필드들 =====

  @Column(name = "adjusted_close", precision = 19, scale = 8, nullable = false)
  private BigDecimal adjustedClose;

  @Column(name = "dividend_amount", precision = 19, scale = 8, nullable = false)
  @Builder.Default
  private BigDecimal dividendAmount = BigDecimal.ZERO;

  @Column(name = "split_coefficient", precision = 19, scale = 8, nullable = false)
  @Builder.Default
  private BigDecimal splitCoefficient = BigDecimal.ONE;
}