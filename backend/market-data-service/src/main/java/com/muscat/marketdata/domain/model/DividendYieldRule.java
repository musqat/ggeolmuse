package com.muscat.marketdata.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

// 배당수익률 계산 기준(from 이후 배당만, 한 번에 종가의 specialRatio 를 넘는 배당은 빼고 수익률이 maxRatio 를 넘으면 비움)
public record DividendYieldRule(LocalDate from, BigDecimal specialRatio, BigDecimal maxRatio) {
}
