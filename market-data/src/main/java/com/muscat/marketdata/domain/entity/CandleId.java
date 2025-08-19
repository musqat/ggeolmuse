package com.muscat.marketdata.domain.entity;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 일봉 복합키: (symbol, date)
 */
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Embeddable
public class CandleId implements Serializable {
  private String symbol;    // 티커
  private LocalDate date;   // 거래일(UTC 저장 권장)
}
