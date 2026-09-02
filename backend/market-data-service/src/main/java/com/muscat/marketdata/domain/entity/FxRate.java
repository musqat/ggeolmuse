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

  @Column(name = "currency_pair", nullable = false, length = 10)
  @Builder.Default
  private String currencyPair = "USD/KRW";

}
