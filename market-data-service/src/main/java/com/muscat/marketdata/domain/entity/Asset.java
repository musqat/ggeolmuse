package com.muscat.marketdata.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.*;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "asset")
public class Asset {

  @Id
  @Column(name = "symbol", nullable = false, length = 16)
  private String symbol;

  @Column(name = "name", nullable = false, length = 256)
  private String name;

  @Column(name = "country", nullable = false, length = 3)
  private String country;  // 국가 코드: KR, US

  @Column(name = "currency", nullable = false, length = 3)
  private String currency; // 통화 코드: KRW, USD

  @Column(name = "asset_type", nullable = false, length = 16)
  private String assetType; // 자산 유형: EQUITY, ETF, BOND, CRYPTO 등

  @Column(name = "market_cap")
  private Long marketCap; // 시가총액 (BIGINT, no decimal precision needed)

  @Column(name = "active", nullable = false)
  @Builder.Default
  private Boolean active = true; // 상장 여부 (true: 상장, false: 상장폐지)

  @Column(name = "delisted_date")
  private LocalDate delistedDate; // 상장폐지일
}
