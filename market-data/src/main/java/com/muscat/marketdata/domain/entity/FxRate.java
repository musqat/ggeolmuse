package com.muscat.marketdata.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "fx_rate")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FxRate {

  @Id
  @Column(nullable = false)
  private LocalDate date;

  @Column(nullable = false, precision = 18, scale = 6)
  private BigDecimal rate;

  /** KRW -> USD 역방향 계산용 */
  public BigDecimal invert() {
    // KRW->USD = 1 / (USD->KRW)
    if (rate == null || BigDecimal.ZERO.compareTo(rate) == 0) {
      throw new IllegalStateException("Rate is null or zero, cannot invert.");
    }
    // 소수·반올림 정책
    return BigDecimal.ONE.divide(rate, 12, java.math.RoundingMode.HALF_UP);
  }

}
