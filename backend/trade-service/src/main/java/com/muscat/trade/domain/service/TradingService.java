package com.muscat.trade.domain.service;

import com.muscat.trade.common.enums.type.PriceType;
import com.muscat.trade.domain.dto.request.IdempotencyKey;
import com.muscat.trade.domain.dto.request.TradingCapacityRequestDto;
import com.muscat.trade.domain.dto.response.TradeResponseDto;
import com.muscat.trade.domain.dto.response.TradingCapacityResponseDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface TradingService {

  // 주식 매수 (가격 유형별, 같은 중복 요청 키는 처음 체결 하나)
  TradeResponseDto buyStock(String userId, Long accountId, String symbol, BigDecimal quantity,
    LocalDate tradeDate, PriceType priceType, BigDecimal manualPrice, IdempotencyKey key);

  // 주식 매수 (중복 요청 키 없음)
  default TradeResponseDto buyStock(String userId, Long accountId, String symbol, BigDecimal quantity,
    LocalDate tradeDate, PriceType priceType, BigDecimal manualPrice) {
    return buyStock(userId, accountId, symbol, quantity, tradeDate, priceType, manualPrice, null);
  }

  // 주식 매도 (가격 유형별, 같은 중복 요청 키는 처음 체결 하나)
  TradeResponseDto sellStock(String userId, Long accountId, String symbol, BigDecimal quantity,
    LocalDate tradeDate, PriceType priceType, BigDecimal manualPrice, IdempotencyKey key);

  // 주식 매도 (중복 요청 키 없음)
  default TradeResponseDto sellStock(String userId, Long accountId, String symbol, BigDecimal quantity,
    LocalDate tradeDate, PriceType priceType, BigDecimal manualPrice) {
    return sellStock(userId, accountId, symbol, quantity, tradeDate, priceType, manualPrice, null);
  }

  // 사용자별 거래 내역 조회
  List<TradeResponseDto> getUserTrades(String userId, int page, int size);

  // 특정 종목 거래 내역 조회
  List<TradeResponseDto> getTradesBySymbol(String userId, String symbol);

  // 매수 가능한 주식 수량 계산
  TradingCapacityResponseDto calculateBuyingCapacity(String userId, TradingCapacityRequestDto request);

  // 매도 가능한 주식 수량 계산
  TradingCapacityResponseDto calculateSellingCapacity(String userId, TradingCapacityRequestDto request);
}