package com.muscat.marketdata.domain.entity;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 배당 이벤트 키: (symbol, exDate)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Embeddable
public class DividendId implements Serializable {

  private String symbol;     // 티커
  private LocalDate exDate;  // 배당락일(Ex-dividend)
}
