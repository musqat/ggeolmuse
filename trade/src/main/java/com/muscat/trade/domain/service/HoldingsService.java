package com.muscat.trade.domain.service;

import com.muscat.trade.domain.dto.response.HoldingResponseDto;
import com.muscat.trade.domain.dto.response.PortfolioSummary;
import java.math.BigDecimal;

import java.util.List;
import java.util.Map;

public interface HoldingsService {

  // 사용자 포트폴리오 조회 (계좌별 필터링 옵션)
  List<HoldingResponseDto> getPortfolio(String userId, String accountId);

  // 특정 종목 보유 현황 조회
  HoldingResponseDto getHoldingBySymbol(String userId, String accountId, String symbol);

  // 포트폴리오 요약 정보 (현재가 포함)
  PortfolioSummary getPortfolioSummary(String userId, Map<String, BigDecimal> currentPrices);

  // 배당 지급 처리
  void processDividend(String userId, String accountId, String symbol, BigDecimal dividendAmount);
}