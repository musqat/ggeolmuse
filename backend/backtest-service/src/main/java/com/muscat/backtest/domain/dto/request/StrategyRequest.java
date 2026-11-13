package com.muscat.backtest.domain.dto.request;

import com.muscat.backtest.common.enums.type.PeriodEnd;
import com.muscat.backtest.common.enums.type.StrategyType;
import java.time.LocalDate;

// 전략 실행 요청의 공통 모양(서비스가 전략을 고르는 전략 타입 포함)
public interface StrategyRequest {

  StrategyType getStrategyType();

  String getSymbol();

  LocalDate getStartDate();

  LocalDate getEndDate();

  String getUserId();

  Boolean getCapitalGainsTax();

  PeriodEnd getPeriodEnd();

  Boolean getYearEndHarvest();

  // 연말 정리를 켜거나 끄고 기간 끝에 파는 사본(이력 주인은 비움)
  StrategyRequest yearEndVariant(boolean harvest);
}
