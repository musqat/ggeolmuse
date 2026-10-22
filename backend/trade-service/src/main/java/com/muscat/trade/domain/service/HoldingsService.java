package com.muscat.trade.domain.service;

import com.muscat.trade.domain.dto.response.HoldingResponseDto;
import com.muscat.trade.domain.dto.response.PortfolioSummary;
import java.math.BigDecimal;

import java.util.List;
import java.util.Map;

public interface HoldingsService {

  // 사용자 포트폴리오 조회 (계좌 ID null인 경우 전체 계좌)
  List<HoldingResponseDto> getPortfolio(String userId, Long accountId);

  // 특정 종목 보유 현황 조회 (없으면 null 반환)
  HoldingResponseDto getHoldingBySymbol(String userId, Long accountId, String symbol);

  // 포트폴리오 요약 정보 계산 (총 투자금, 평가액, 수익률 등)
  PortfolioSummary getPortfolioSummary(String userId, Map<String, BigDecimal> currentPrices);

}