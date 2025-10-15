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

  // 백테스트 결과와 함께 포트폴리오 요약 정보 조회 (직접 백테스트 클라이언트 호출)
  PortfolioSummary getPortfolioSummaryWithBacktest(String userId, Map<String, BigDecimal> currentPrices, String authorization);

  // 거래 이력과 연관된 보유 종목 조회
  List<HoldingResponseDto> getHoldingsWithTradeHistory(String userId, String symbol, Integer minQuantity);

  // 수익률 기준 상위 N개 종목 조회
  List<HoldingResponseDto> getTopPerformingHoldings(String userId, int limit);

  // 특정 금액 이상 투자한 종목들 조회
  List<HoldingResponseDto> getHoldingsByMinInvestment(String userId, BigDecimal minAmount);

}