package com.muscat.trade.api.controller;

import com.muscat.trade.domain.dto.response.DividendResponseDto;
import com.muscat.trade.domain.dto.response.TransactionHistoryItem;
import com.muscat.trade.domain.dto.response.TransactionHistoryItem.DividendItem;
import com.muscat.trade.domain.dto.response.TransactionHistoryItem.TradeItem;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.repository.TradeRepository;
import com.muscat.trade.domain.service.DividendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
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
  public ResponseEntity<List<TransactionHistoryItem>> getTransactionHistory(
    @AuthenticationPrincipal Jwt jwt) {

    String userId = jwt.getSubject();
    log.info("거래 내역 조회: userId={}", userId);

    // 매수 · 매도 최근 1000 건과 배당 몫 전부
    List<Trade> trades = tradeRepository.findByUserIdOrderByExecutedAtDesc(userId, PageRequest.of(0, 1000))
      .getContent();
    List<DividendResponseDto> dividends = dividendService.getUserDividends(userId);

    List<TransactionHistoryItem> transactions = new ArrayList<>(trades.size() + dividends.size());
    trades.forEach(trade -> transactions.add(TradeItem.from(trade)));
    dividends.forEach(dividend -> transactions.add(DividendItem.from(dividend)));
    // 거래일 최신순(같은 날은 매매 · 배당 순서 그대로)
    transactions.sort(Comparator.comparing(TransactionHistoryItem::date,
      Comparator.nullsLast(Comparator.reverseOrder())));

    log.info("거래 내역 조회 완료: {} 건 (매수/매도: {}, 배당: {})",
      transactions.size(), trades.size(), dividends.size());

    return ResponseEntity.ok(transactions);
  }
}
