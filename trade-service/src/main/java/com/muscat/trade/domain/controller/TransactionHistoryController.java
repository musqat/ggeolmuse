package com.muscat.trade.domain.controller;

import com.muscat.trade.domain.dto.response.DividendResponseDto;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.repository.TradeRepository;
import com.muscat.trade.domain.service.DividendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "거래 내역", description = "통합 거래 내역 조회 API (매수/매도/배당)")
public class TransactionHistoryController {

  private final TradeRepository tradeRepository;
  private final DividendService dividendService;

  @Operation(
    summary = "통합 거래 내역 조회",
    description = "매수, 매도, 배당 수령 내역을 시간순으로 통합하여 조회합니다. 배당 내역은 캐싱됩니다."
  )
  @GetMapping("/history")
  public ResponseEntity<List<Map<String, Object>>> getTransactionHistory(
    @AuthenticationPrincipal Jwt jwt) {

    String userId = jwt.getSubject();
    log.info("거래 내역 조회: userId={}", userId);

    List<Map<String, Object>> transactions = new ArrayList<>();

    // 1. 매수/매도 거래 조회
    List<Trade> trades = tradeRepository.findByUserIdOrderByExecutedAtDesc(
      userId,
      org.springframework.data.domain.PageRequest.of(0, 1000)
    ).getContent();

    for (Trade trade : trades) {
      Map<String, Object> transaction = new HashMap<>();
      transaction.put("type", trade.getTradeType().toString()); // BUY or SELL
      transaction.put("tradeId", trade.getId()); // Trade ID
      transaction.put("accountId", trade.getAccountId()); // 계좌 ID
      transaction.put("symbol", trade.getSymbol());
      transaction.put("quantity", trade.getQuantity());
      transaction.put("price", trade.getPrice());
      transaction.put("totalAmount", trade.getTotalAmount());
      transaction.put("fee", trade.getFee());
      transaction.put("date", trade.getTradeDate());
      transaction.put("executedAt", trade.getExecutedAt());
      transactions.add(transaction);
    }

    // 2. 배당 수령 내역 조회 (Trade 단위로 캐싱됨)
    List<DividendResponseDto> dividends = dividendService.getUserDividends(userId);

    for (DividendResponseDto dividend : dividends) {
      Map<String, Object> transaction = new HashMap<>();
      transaction.put("type", "DIVIDEND"); // 배당
      transaction.put("tradeId", dividend.tradeId()); // 연결된 Trade ID
      transaction.put("symbol", dividend.symbol());
      transaction.put("quantity", null); // 배당은 수량 없음
      transaction.put("price", null); // 배당은 단가 없음
      transaction.put("totalAmount", dividend.netAmount()); // 세후 금액
      transaction.put("grossAmount", dividend.grossAmount()); // 세전 금액
      transaction.put("taxAmount", dividend.taxAmount()); // 원천징수세
      transaction.put("fee", null);
      transaction.put("date", dividend.dividendDate());
      transaction.put("executedAt", dividend.processedAt());
      transaction.put("dividendPerShare", dividend.dividendPerShare());
      transaction.put("shares", dividend.shares()); // 배당 받은 주식 수
      transactions.add(transaction);
    }

    // 3. 날짜순 정렬 (최신순)
    transactions.sort((a, b) -> {
      Object dateA = a.get("date");
      Object dateB = b.get("date");
      if (dateA instanceof Comparable && dateB instanceof Comparable) {
        return ((Comparable) dateB).compareTo(dateA); // 내림차순
      }
      return 0;
    });

    log.info("거래 내역 조회 완료: {} 건 (매수/매도: {}, 배당: {})",
      transactions.size(), trades.size(), dividends.size());

    return ResponseEntity.ok(transactions);
  }
}
