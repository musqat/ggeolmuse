package com.muscat.trade.domain.controller;

import com.muscat.trade.domain.dto.request.TradeRequestDto;
import com.muscat.trade.domain.dto.response.TradeResponseDto;
import com.muscat.trade.domain.service.TradingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/trade")
@RequiredArgsConstructor
@Slf4j
public class TradingController {

  private final TradingService tradingService;

  // 주식 매수
  @PostMapping("/buy")
  public ResponseEntity<TradeResponseDto> buyStock(
      Authentication auth,
      @Valid @RequestBody TradeRequestDto request) {

    String userId = extractUserId(auth);

    log.info("매수 요청: userId={}, accountId={}, symbol={}, quantity={}, tradeDate={}", 
        userId, request.getAccountId(), request.getSymbol(), request.getQuantity(), request.getTradeDate());

    TradeResponseDto trade = tradingService.buyStock(
        userId, 
        request.getAccountId(), 
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
      Authentication auth,
      @Valid @RequestBody TradeRequestDto request) {

    String userId = extractUserId(auth);

    log.info("매도 요청: userId={}, accountId={}, symbol={}, quantity={}, tradeDate={}", 
        userId, request.getAccountId(), request.getSymbol(), request.getQuantity(), request.getTradeDate());

    TradeResponseDto trade = tradingService.sellStock(
        userId, 
        request.getAccountId(), 
        request.getSymbol(), 
        request.getQuantity(), 
        request.getTradeDate(),
        request.getPriceType(),
        request.getManualPrice()
    );

    return ResponseEntity.ok(trade);
  }

  // 거래 내역 조회 (페이지네이션)
  @GetMapping("/history")
  public ResponseEntity<List<TradeResponseDto>> getTradeHistory(
      Authentication auth,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {

    String userId = extractUserId(auth);

    List<TradeResponseDto> trades = tradingService.getUserTrades(userId, page, size);

    return ResponseEntity.ok(trades);
  }

  // 종목별 거래 내역 조회
  @GetMapping("/history/{symbol}")
  public ResponseEntity<List<TradeResponseDto>> getTradeHistoryBySymbol(
      Authentication auth,
      @PathVariable String symbol) {

    String userId = extractUserId(auth);

    List<TradeResponseDto> trades = tradingService.getTradesBySymbol(userId, symbol);

    return ResponseEntity.ok(trades);
  }

  // 기간별 거래 내역 조회
  @GetMapping("/history/period")
  public ResponseEntity<List<TradeResponseDto>> getTradeHistoryByPeriod(
      Authentication auth,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

    String userId = extractUserId(auth);

    List<TradeResponseDto> trades = tradingService.getTradesByDateRange(userId, startDate, endDate);

    return ResponseEntity.ok(trades);
  }

  // 매수 가능 여부 확인
  @GetMapping("/can-buy")
  public ResponseEntity<Boolean> canBuyStock(
      Authentication auth,
      @RequestParam String accountId,
      @RequestParam String totalAmount) {

    String userId = extractUserId(auth);

    boolean canBuy = tradingService.canBuyStock(userId, accountId, 
        java.math.BigDecimal.valueOf(Double.parseDouble(totalAmount)));

    return ResponseEntity.ok(canBuy);
  }

  // 매도 가능 여부 확인
  @GetMapping("/can-sell")
  public ResponseEntity<Boolean> canSellStock(
      Authentication auth,
      @RequestParam String accountId,
      @RequestParam String symbol,
      @RequestParam String quantity) {

    String userId = extractUserId(auth);

    boolean canSell = tradingService.canSellStock(userId, accountId, symbol,
        java.math.BigDecimal.valueOf(Double.parseDouble(quantity)));

    return ResponseEntity.ok(canSell);
  }

  // JWT에서 사용자 ID 추출
  private String extractUserId(Authentication auth) {
    Jwt jwt = (Jwt) auth.getPrincipal();
    return jwt.getClaimAsString("sub");
  }
}