package com.muscat.trade.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 옛 매수 건별 배당 캐시 표(dividends). 계좌를 지울 때 그 계좌 행만 지움
@Entity
@Table(name = "dividends")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LegacyDividend {

  @Id
  private Long id;

  @Column(name = "account_id", nullable = false)
  private Long accountId;
}
