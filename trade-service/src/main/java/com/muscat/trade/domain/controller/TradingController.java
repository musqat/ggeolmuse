package com.muscat.trade.domain.controller;

import com.muscat.trade.domain.dto.request.TradeRequestDto;
import com.muscat.trade.domain.dto.request.TradingCapacityRequestDto;
import com.muscat.trade.domain.dto.response.TradeResponseDto;
import com.muscat.trade.domain.dto.response.TradingCapacityResponseDto;
import com.muscat.trade.domain.service.TradingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trade")
@RequiredArgsConstructor
@Slf4j
public class TradingController {

  private final TradingService tradingService;

  // 주식 매수
  @PostMapping("/buy")
  public ResponseEntity<TradeResponseDto> buyStock(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody TradeRequestDto request) {

    String userId = jwt.getSubject();

    log.info("매수 요청: userId={}, accountId={}, symbol={}, quantity={}, tradeDate={}",
        userId, request.getAccountId(), request.getSymbol(), request.getQuantity(),
        request.getTradeDate());

    TradeResponseDto trade = tradingService.buyStock(
        userId,
        Long.valueOf(request.getAccountId()),
        request.getSymbol(),
        request.getQuantity(),
        request.getTradeDate(),
        request.getPriceType(),
        request.getManualPrice()
    );

    return ResponseEntity.ok(trade);
  }

  // 주식 매도
  @PostMapping("/sell")
  public ResponseEntity<TradeResponseDto> sellStock(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody TradeRequestDto request) {

    String userId = jwt.getSubject();

    log.info("매도 요청: userId={}, accountId={}, symbol={}, quantity={}, tradeDate={}",
        userId, request.getAccountId(), request.getSymbol(), request.getQuantity(),
        request.getTradeDate());

    TradeResponseDto trade = tradingService.sellStock(
        userId,
        Long.valueOf(request.getAccountId()),
        request.getSymbol(),
        request.getQuantity(),
        request.getTradeDate(),
        request.getPriceType(),
        request.getManualPrice()
    );

    return ResponseEntity.ok(trade);
  }


  // 매수 가능한 주식 수량 계산
  @PostMapping("/can-buy")
  public ResponseEntity<TradingCapacityResponseDto> calculateBuyingCapacity(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody TradingCapacityRequestDto request) {

    String userId = jwt.getSubject();

    log.info("매수 가능 수량 계산 요청: userId={}, accountId={}, symbol={}, tradeDate={}",
        userId, request.getAccountId(), request.getSymbol(), request.getTradeDate());

    TradingCapacityResponseDto result = tradingService.calculateBuyingCapacity(userId, request);

    return ResponseEntity.ok(result);
  }

  // 매도 가능한 주식 수량 계산
  @PostMapping("/can-sell")
  public ResponseEntity<TradingCapacityResponseDto> calculateSellingCapacity(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody TradingCapacityRequestDto request) {

    String userId = jwt.getSubject();

    log.info("매도 가능 수량 계산 요청: userId={}, accountId={}, symbol={}, tradeDate={}",
        userId, request.getAccountId(), request.getSymbol(), request.getTradeDate());

    TradingCapacityResponseDto result = tradingService.calculateSellingCapacity(userId, request);

    return ResponseEntity.ok(result);
  }

}