package com.muscat.trade.domain.service.impl;

import com.muscat.commonlib.constants.CommonConstants;
import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.domain.dto.response.DividendResponseDto;
import com.muscat.trade.domain.entity.Dividend;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.repository.DividendRepository;
import com.muscat.trade.domain.repository.TradeRepository;
import com.muscat.trade.domain.service.DividendService;
import com.muscat.trade.infra.client.MarketServiceClient;
import com.muscat.trade.infra.client.dto.DividendDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DividendServiceImpl implements DividendService {

  private final DividendRepository dividendRepository;
  private final TradeRepository tradeRepository;
  private final MarketServiceClient marketServiceClient;

  private static final BigDecimal TAX_RATE = new BigDecimal("0.154"); // 15.4% 원천징수

  @Override
  @Transactional
  public List<DividendResponseDto> getUserDividends(String userId) {
    log.info("조회: 사용자 {} 배당 내역", userId);

    // Trade 테이블에서 거래한 모든 고유 종목 조회 (과거 보유 종목 포함)
    List<String> allSymbols = tradeRepository.findDistinctSymbolsByUserId(userId);
    log.info("거래 종목 수: {}", allSymbols.size());

    List<DividendResponseDto> allDividends = new ArrayList<>();

    for (String symbol : allSymbols) {
      // 해당 종목의 첫 매수일 찾기
      LocalDate firstPurchaseDate = getFirstPurchaseDate(userId, symbol);

      // 배당 조회 및 캐싱
      List<DividendResponseDto> dividends = getDividendsWithCache(
        userId,
        symbol,
        firstPurchaseDate,
        LocalDate.now()
      );

      allDividends.addAll(dividends);
    }

    log.info("총 배당 내역: {} 건", allDividends.size());
    return allDividends;
  }

  @Override
  @Transactional
  public List<DividendResponseDto> getDividendsWithCache(String userId, String symbol,
      LocalDate startDate, LocalDate endDate) {

    log.debug("배당 조회 (Trade 단위 캐싱): userId={}, symbol={}, startDate={}, endDate={}",
        userId, symbol, startDate, endDate);

    // 1. 해당 종목의 모든 BUY Trade 조회 (시간순)
    List<Trade> buyTrades = tradeRepository
        .findByUserIdAndSymbolOrderByTradeDateAsc(userId, symbol)
        .stream()
        .filter(t -> t.getTradeType() == TradeType.BUY)
        .toList();

    if (buyTrades.isEmpty()) {
      log.debug("매수 내역 없음: {}", symbol);
      return new ArrayList<>();
    }

    // 2. Market-data에서 배당 이벤트 조회
    log.info("Market-data에서 배당 조회: {}", symbol);
    List<DividendDto> marketDividends;
    try {
      marketDividends = marketServiceClient.getDividends(
          symbol,
          startDate.toString(),
          endDate.toString()
      );
    } catch (Exception e) {
      log.error("배당 조회 실패: {}", e.getMessage());
      // 실패 시 캐시된 데이터라도 반환
      return dividendRepository
          .findByUserIdAndSymbolOrderByDividendDateDesc(userId, symbol)
          .stream()
          .map(DividendResponseDto::from)
          .toList();
    }

    log.info("배당 이벤트 {} 건 조회됨", marketDividends.size());

    // 3. 각 배당일마다, 각 Trade별로 배당 레코드 생성
    for (DividendDto marketDividend : marketDividends) {
      LocalDate dividendDate = LocalDate.parse(marketDividend.exDate());

      for (Trade buyTrade : buyTrades) {
        // 배당 기준일 이후에 매수한 경우 skip
        if (buyTrade.getTradeDate().isAfter(dividendDate)) {
          continue;
        }

        // 이미 저장된 배당인지 확인 (Trade + 배당일 단위)
        if (dividendRepository.existsByTradeIdAndDividendDate(
            buyTrade.getId(), dividendDate)) {
          continue;
        }

        // 배당 기준일에 이 Trade의 주식이 얼마나 남아있는지 계산
        BigDecimal sharesRemaining = calculateTradeSharesRemainingAt(
            userId, symbol, buyTrade.getId(), dividendDate);

        if (sharesRemaining.compareTo(BigDecimal.ZERO) <= 0) {
          continue; // 이미 전량 매도됨
        }

        // 배당금 계산 (이 Trade의 남은 주식에 대해서만)
        BigDecimal grossAmount = marketDividend.amount().multiply(sharesRemaining)
            .setScale(CommonConstants.DEFAULT_SCALE, CommonConstants.DEFAULT_ROUNDING_MODE);
        BigDecimal taxAmount = grossAmount.multiply(TAX_RATE)
            .setScale(CommonConstants.DEFAULT_SCALE, CommonConstants.DEFAULT_ROUNDING_MODE);
        BigDecimal netAmount = grossAmount.subtract(taxAmount);

        // Dividend 엔티티 생성 및 저장 (tradeId 연결)
        Dividend dividend = Dividend.builder()
            .userId(userId)
            .accountId(buyTrade.getAccountId())
            .symbol(symbol)
            .tradeId(buyTrade.getId())
            .shares(sharesRemaining)
            .dividendPerShare(marketDividend.amount())
            .grossAmount(grossAmount)
            .taxAmount(taxAmount)
            .netAmount(netAmount)
            .dividendDate(dividendDate)
            .processedAt(LocalDateTime.now())
            .build();

        dividendRepository.save(dividend);

        log.info("배당 캐싱: {} Trade[{}] - {} shares × ${} = ${} (세후: ${})",
            symbol, buyTrade.getId(),
            sharesRemaining, marketDividend.amount(), grossAmount, netAmount);
      }
    }

    // 3.5. 모든 배당 저장 완료 후 DB에 flush
    dividendRepository.flush();

    // 4. 전체 배당 재조회 후 DTO 변환하여 반환
    return dividendRepository
        .findByUserIdAndSymbolOrderByDividendDateDesc(userId, symbol)
        .stream()
        .map(DividendResponseDto::from)
        .toList();
  }

  @Override
  @Transactional
  public void refreshDividendCache(String userId) {
    log.info("배당 캐시 강제 갱신: userId={}", userId);

    // Trade 테이블에서 거래한 모든 고유 종목에 대해 배당 캐시 갱신
    List<String> allSymbols = tradeRepository.findDistinctSymbolsByUserId(userId);

    for (String symbol : allSymbols) {
      LocalDate firstPurchaseDate = getFirstPurchaseDate(userId, symbol);
      getDividendsWithCache(userId, symbol, firstPurchaseDate, LocalDate.now());
    }
  }

  /**
   * 특정 Trade의 주식이 특정 날짜에 얼마나 남아있는지 계산 FIFO (First-In-First-Out) 방식으로 매도 처리
   * <p>
   * 예: Trade1 (10주 매수) → 매도 7주 → 배당일에 3주 남음
   */
  private BigDecimal calculateTradeSharesRemainingAt(
    String userId, String symbol, Long targetTradeId, LocalDate targetDate) {

    // 모든 거래 내역 조회 (시간순)
    List<Trade> allTrades = tradeRepository
      .findByUserIdAndSymbolOrderByTradeDateAsc(userId, symbol);

    // targetTradeId의 매수 수량
    BigDecimal originalShares = allTrades.stream()
      .filter(t -> t.getId().equals(targetTradeId) && t.getTradeType() == TradeType.BUY)
      .map(Trade::getQuantity)
      .findFirst()
      .orElse(BigDecimal.ZERO);

    if (originalShares.compareTo(BigDecimal.ZERO) <= 0) {
      return BigDecimal.ZERO;
    }

    // targetDate까지의 매도를 FIFO로 처리하여 이 Trade의 남은 수량 계산
    BigDecimal remainingShares = originalShares;
    boolean reachedTargetTrade = false;

    for (Trade trade : allTrades) {
      // targetDate 이후의 거래는 무시
      if (trade.getTradeDate().isAfter(targetDate)) {
        break;
      }

      // targetTrade를 만나기 전까지는 skip
      if (!reachedTargetTrade) {
        if (trade.getId().equals(targetTradeId)) {
          reachedTargetTrade = true;
        }
        continue;
      }

      // targetTrade 이후의 매도만 처리 (FIFO)
      if (trade.getTradeType() == TradeType.SELL) {
        remainingShares = remainingShares.subtract(trade.getQuantity());
        if (remainingShares.compareTo(BigDecimal.ZERO) < 0) {
          remainingShares = BigDecimal.ZERO;
          break;
        }
      }
    }

    return remainingShares;
  }

  /**
   * 첫 매수일 조회
   */
  private LocalDate getFirstPurchaseDate(String userId, String symbol) {
    List<Trade> trades = tradeRepository
      .findByUserIdAndSymbolOrderByTradeDateAsc(userId, symbol);

    if (trades.isEmpty()) {
      return LocalDate.now();
    }

    return trades.getFirst().getTradeDate();
  }

}
