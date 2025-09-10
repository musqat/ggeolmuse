package com.muscat.trade.domain.service;

import com.muscat.trade.common.enums.type.PriceType;
import com.muscat.trade.domain.dto.response.TradeResponseDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface TradingService {

  // 주식 매수 (가격 유형별)
  TradeResponseDto buyStock(String userId, Long accountId, String symbol, 
                BigDecimal quantity, LocalDate tradeDate, PriceType priceType, BigDecimal manualPrice);

  // 주식 매도 (가격 유형별)
  TradeResponseDto sellStock(String userId, Long accountId, String symbol, 
                 BigDecimal quantity, LocalDate tradeDate, PriceType priceType, BigDecimal manualPrice);

  // 사용자별 거래 내역 조회
  List<TradeResponseDto> getUserTrades(String userId, int page, int size);

  // 특정 종목 거래 내역 조회
  List<TradeResponseDto> getTradesBySymbol(String userId, String symbol);

  // 특정 기간 거래 내역 조회
  List<TradeResponseDto> getTradesByDateRange(String userId, LocalDate startDate, LocalDate endDate);

  // 거래 가능 여부 검증 (잔액, 보유량 체크)
  boolean canBuyStock(String userId, Long accountId, BigDecimal totalAmount);
  
  boolean canSellStock(String userId, Long accountId, String symbol, BigDecimal quantity);
}