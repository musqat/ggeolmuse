package com.muscat.marketdata.domain.entity;

import jakarta.persistence.*;
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

  @Column(name = "name", nullable = false, length = 128)
  private String name;

  @Column(name = "country", nullable = false, length = 3)
  private String country;  // 국가 코드: KR, US

  @Column(name = "currency", nullable = false, length = 3)
  private String currency; // 통화 코드: KRW, USD

  @Column(name = "asset_type", nullable = false, length = 16)
  private String assetType; // 자산 유형: EQUITY, ETF, BOND, CRYPTO 등

  @Column(name = "market_cap", precision = 19, scale = 8)
  private Long marketCap; // 시가총액
}
