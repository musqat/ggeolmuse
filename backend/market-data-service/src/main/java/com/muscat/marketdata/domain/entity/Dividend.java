package com.muscat.marketdata.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "dividend",
    uniqueConstraints = @UniqueConstraint(columnNames = {"symbol", "ex_date"}),
    indexes = {
        @Index(name = "idx_dividend_symbol_ex_date", columnList = "symbol,ex_date"),
        @Index(name = "idx_dividend_ex_date", columnList = "ex_date")
    })
public class Dividend {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "symbol", nullable = false, length = 16)
  private String symbol;

  @Column(name = "ex_date", nullable = false)
  private LocalDate exDate;

  // 주당 배당금액
  @Column(name = "amount", precision = 19, scale = 8, nullable = false)
  private BigDecimal amount;

  // 배당 통화 (기본값: USD)
  @Column(name = "currency", length = 3, nullable = false)
  @Builder.Default
  private String currency = "USD";

}