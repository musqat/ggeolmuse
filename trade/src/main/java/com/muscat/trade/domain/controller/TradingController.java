package com.muscat.trade.domain.controller;

import com.muscat.trade.domain.dto.request.TradeRequestDto;
import com.muscat.trade.domain.dto.response.TradeResponseDto;
import com.muscat.trade.domain.service.TradingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
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
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody TradeRequestDto request) {

    String userId = jwt.getSubject();

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
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody TradeRequestDto request) {

    String userId = jwt.getSubject();

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
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {

    String userId = jwt.getSubject();

    List<TradeResponseDto> trades = tradingService.getUserTrades(userId, page, size);

    return ResponseEntity.ok(trades);
  }

  // 종목별 거래 내역 조회
  @GetMapping("/history/{symbol}")
  public ResponseEntity<List<TradeResponseDto>> getTradeHistoryBySymbol(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable String symbol) {

    String userId = jwt.getSubject();

    List<TradeResponseDto> trades = tradingService.getTradesBySymbol(userId, symbol);

    return ResponseEntity.ok(trades);
  }

  // 기간별 거래 내역 조회
  @GetMapping("/history/period")
  public ResponseEntity<List<TradeResponseDto>> getTradeHistoryByPeriod(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

    String userId = jwt.getSubject();

    List<TradeResponseDto> trades = tradingService.getTradesByDateRange(userId, startDate, endDate);

    return ResponseEntity.ok(trades);
  }

  // 매수 가능 여부 확인
  @GetMapping("/can-buy")
  public ResponseEntity<Boolean> canBuyStock(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam String accountId,
      @RequestParam String totalAmount) {

    String userId = jwt.getSubject();

    // 입력 검증 및 안전한 형변환
    BigDecimal amount;
    try {
      amount = new BigDecimal(totalAmount);
      if (amount.compareTo(BigDecimal.ZERO) <= 0) {
        throw new IllegalArgumentException("금액은 0보다 커야 합니다");
      }
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("유효하지 않은 금액 형식입니다: " + totalAmount);
    }

    boolean canBuy = tradingService.canBuyStock(userId, accountId, amount);

    return ResponseEntity.ok(canBuy);
  }

  // 매도 가능 여부 확인
  @GetMapping("/can-sell")
  public ResponseEntity<Boolean> canSellStock(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam String accountId,
      @RequestParam String symbol,
      @RequestParam String quantity) {

    String userId = jwt.getSubject();

    // 입력 검증 및 안전한 형변환
    BigDecimal qty;
    try {
      qty = new BigDecimal(quantity);
      if (qty.compareTo(BigDecimal.ZERO) <= 0) {
        throw new IllegalArgumentException("수량은 0보다 커야 합니다");
      }
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("유효하지 않은 수량 형식입니다: " + quantity);
    }

    boolean canSell = tradingService.canSellStock(userId, accountId, symbol, qty);

    return ResponseEntity.ok(canSell);
  }

}