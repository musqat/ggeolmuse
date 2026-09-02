package com.muscat.marketdata.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
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

  // ===== 최신 캔들 비정규화 (요약 조회 성능용) =====
  // candle 테이블 2967만 행에서 매 요청마다 종목별 최신가 조회하면 느림.
  // 캔들 저장 시 여기에 캐싱해 두면 summary는 asset 테이블만 조회하면 됨.

  @Column(name = "latest_close", precision = 19, scale = 8)
  private BigDecimal latestClose; // 최신 종가

  @Column(name = "latest_date")
  private LocalDate latestDate; // 최신 캔들 날짜
}
