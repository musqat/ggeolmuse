package com.muscat.backtest.domain.dto.request;

import com.muscat.backtest.common.enums.type.PortfolioStrategyType;
import io.swagger.v3.oas.annotations.media.Schema;

// 포트폴리오 종목 줄 전략
@Schema(description = "포트폴리오 종목 줄 전략")
public record PortfolioStrategy(
  @Schema(description = "전략 (LUMP_SUM 기간 안 첫 거래일에 금액 전부, DCA 요청의 적립식 주기로 회차일에 회당 금액, 비우면 요청의 매수 방식)", example = "DCA")
  PortfolioStrategyType type
) {}
