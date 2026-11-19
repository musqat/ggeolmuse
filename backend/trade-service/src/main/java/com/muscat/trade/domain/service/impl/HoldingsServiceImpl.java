package com.muscat.trade.domain.service.impl;

import com.muscat.commonlib.dto.StockPriceDto;
import com.muscat.trade.common.constants.TradeConstants;
import com.muscat.trade.common.logging.TradeLogger;
import com.muscat.trade.domain.dto.TradePosition;
import com.muscat.trade.domain.dto.response.HoldingResponseDto;
import com.muscat.trade.domain.dto.response.PortfolioSummary;
import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.domain.entity.Holdings;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.repository.HoldingsRepository;
import com.muscat.trade.domain.repository.TradeRepository;
import com.muscat.trade.domain.service.DividendEntitlement;
import com.muscat.trade.domain.service.DividendPayoutService;
import com.muscat.trade.domain.service.HoldingsService;
import com.muscat.trade.infra.client.MarketServiceClientWrapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class HoldingsServiceImpl implements HoldingsService {

  private final HoldingsRepository holdingsRepository;
  private final TradeLogger tradeLogger;
  private final MarketServiceClientWrapper marketServiceClientWrapper;
  private final DividendPayoutService dividendPayoutService;
  private final TradeRepository tradeRepository;

  @Override
  public List<HoldingResponseDto> getPortfolio(String userId, Long accountId) {
    log.debug("포트폴리오 조회: userId={}, accountId={}", userId, accountId);

    List<Holdings> holdings = accountId != null
      ? holdingsRepository.findByUserIdAndAccountId(userId, accountId)
      : holdingsRepository.findByUserId(userId);

    String logType = accountId != null ? "ACCOUNT_PORTFOLIO" : "USER_PORTFOLIO";
    tradeLogger.logPortfolioAccess(userId, logType, accountId != null ? accountId.toString() : null,
      holdings.size());

    Map<String, BigDecimal> currentPrices = currentPricesOf(holdings);

    Map<TradePosition, BigDecimal> dividends = holdings.isEmpty()
      ? Map.of()
      : dividendPayoutService.dividendsOnHeldShares(userId);
    Map<TradePosition, BigDecimal> splitRatios = holdings.isEmpty() ? Map.of() : splitRatiosOf(userId);

    // 현재가 정보와 함께 DTO 생성
    return holdings.stream()
      .map(holding -> {
        BigDecimal currentPrice = currentPrices.get(holding.getSymbol());
        HoldingResponseDto dto = currentPrice != null
          ? HoldingResponseDto.of(holding, currentPrice)
          : HoldingResponseDto.from(holding);
        TradePosition position = new TradePosition(userId, holding.getAccountId(), holding.getSymbol());
        return dto.withTotalDividends(dividends.getOrDefault(position, BigDecimal.ZERO))
          .withSplitRatio(splitRatios.get(position));
      })
      .collect(Collectors.toList());
  }

  // 자리마다 지금 남은 매수 묶음 가운데 분할로 맞춘 가장 이른 체결의 누적 비율(없으면 자리 없음)
  private Map<TradePosition, BigDecimal> splitRatiosOf(String userId) {
    Map<TradePosition, List<Trade>> tradesByPosition = tradeRepository
      .findByUserIdAndStatusOrderByExecutedAtAscIdAsc(userId, TradeStatus.COMPLETED).stream()
      .collect(Collectors.groupingBy(t -> new TradePosition(userId, t.getAccountId(), t.getSymbol())));
    Map<TradePosition, BigDecimal> ratios = new HashMap<>();
    tradesByPosition.forEach((position, trades) ->
      DividendEntitlement.remainingByBuyBefore(trades, LocalDate.MAX).stream()
        .map(lot -> lot.buy().getSplitRatio())
        .filter(Objects::nonNull)
        .findFirst()
        .ifPresent(ratio -> ratios.put(position, ratio)));
    return ratios;
  }

  @Override
  @Transactional(readOnly = true)
  public HoldingResponseDto getHoldingBySymbol(String userId, Long accountId, String symbol) {
    Optional<Holdings> holding = holdingsRepository.findByUserIdAndAccountIdAndSymbol(userId,
      accountId, symbol);

    if (holding.isEmpty()) {
      log.warn("보유종목 없음: userId={}, accountId={}, symbol={}", userId, accountId, symbol);
      tradeLogger.logPortfolioAccess(userId, "SYMBOL_HOLDING", accountId + ":" + symbol, 0);
      return null;
    }

    tradeLogger.logPortfolioAccess(userId, "SYMBOL_HOLDING", accountId + ":" + symbol, 1);
    return HoldingResponseDto.from(holding.get());
  }


  @Override
  public PortfolioSummary getPortfolioSummary(String userId) {
    List<Holdings> portfolio = holdingsRepository.findByUserId(userId);
    return summarize(userId, portfolio, currentPricesOf(portfolio));
  }

  // 보유 종목의 현재가를 한 번에(시세 서비스가 안 되면 대체 동작의 빈 맵 → 현재가 없이)
  private Map<String, BigDecimal> currentPricesOf(List<Holdings> holdings) {
    List<String> symbols = holdings.stream()
      .map(Holdings::getSymbol)
      .distinct()
      .toList();

    Map<String, BigDecimal> currentPrices = new HashMap<>();
    if (!symbols.isEmpty()) {
      try {
        Map<String, StockPriceDto> prices = marketServiceClientWrapper.getCurrentPrices(symbols);
        if (prices != null) {
          prices.forEach((symbol, priceDto) -> {
            if (priceDto != null && priceDto.currentPrice() != null) {
              currentPrices.put(symbol, priceDto.currentPrice());
            }
          });
        }
      } catch (Exception e) {
        log.warn("현재가 일괄 조회 실패: symbols={}, error={}", symbols, e.getMessage());
      }
    }
    log.debug("현재가 조회 완료: totalSymbols={}, successCount={}", symbols.size(), currentPrices.size());
    return currentPrices;
  }

  private PortfolioSummary summarize(String userId, List<Holdings> portfolio,
                                     Map<String, BigDecimal> currentPrices) {
    log.debug("포트폴리오 종합 정보 계산: userId={}", userId);

    var summaryProjection = holdingsRepository.calculatePortfolioSummary(userId);
    BigDecimal totalInvested =
      summaryProjection != null ? summaryProjection.totalInvestedAmount() : BigDecimal.ZERO;
    int holdingCount = summaryProjection != null ? summaryProjection.holdingCount() : 0;

    BigDecimal totalCurrentValue = BigDecimal.ZERO;
    BigDecimal pricedInvested = BigDecimal.ZERO;
    List<String> unpricedSymbols = new ArrayList<>();

    // 보유 종목별 상세 정보 및 계산
    List<HoldingResponseDto> holdings = new ArrayList<>();
    Map<String, BigDecimal> symbolReturnRates = new HashMap<>();
    Map<String, BigDecimal> symbolUnrealizedPnL = new HashMap<>();

    for (Holdings holding : portfolio) {
      BigDecimal currentPrice = currentPrices.get(holding.getSymbol());

      // 현재가가 있는 경우만 계산
      if (currentPrice != null && currentPrice.compareTo(BigDecimal.ZERO) > 0) {
        // 평가액 계산
        BigDecimal holdingValue = holding.getTotalQuantity().multiply(currentPrice);
        totalCurrentValue = totalCurrentValue.add(holdingValue);
        pricedInvested = pricedInvested.add(holding.getTotalInvestedAmount());

        // 종목별 평가손익 · 수익률(총 투자금액 기준)
        BigDecimal pnl = holdingValue.subtract(holding.getTotalInvestedAmount());
        symbolUnrealizedPnL.put(holding.getSymbol(), pnl);
        if (holding.getTotalInvestedAmount().signum() > 0) {
          symbolReturnRates.put(holding.getSymbol(),
            HoldingResponseDto.returnRate(pnl, holding.getTotalInvestedAmount()));
        }

        // 현재가 포함한 보유 정보
        holdings.add(HoldingResponseDto.of(holding, currentPrice));
      } else {
        log.warn("현재가 정보 없음: symbol={}", holding.getSymbol());
        if (!unpricedSymbols.contains(holding.getSymbol())) {
          unpricedSymbols.add(holding.getSymbol());
        }
        holdings.add(HoldingResponseDto.from(holding));
      }
    }

    // 총 손익 · 수익률은 현재가 있는 종목의 투자금 기준(현재가 없는 종목을 손실로 세지 않음)
    BigDecimal totalUnrealizedPnL = totalCurrentValue.subtract(pricedInvested);
    BigDecimal totalReturnRate = BigDecimal.ZERO;
    if (pricedInvested.compareTo(BigDecimal.ZERO) > 0) {
      totalReturnRate = totalUnrealizedPnL
        .divide(pricedInvested, 4, RoundingMode.HALF_UP)
        .multiply(TradeConstants.PERCENTAGE_MULTIPLIER);
    }

    // 포트폴리오 요약 생성
    PortfolioSummary summary = PortfolioSummary.builder()
      .totalInvestedAmount(totalInvested)
      .totalCurrentValue(totalCurrentValue)
      .totalUnrealizedPnL(totalUnrealizedPnL)
      .totalReturnRate(totalReturnRate)
      .holdingCount(holdingCount) // QueryDSL 결과 사용
      .holdings(holdings)
      .symbolReturnRates(symbolReturnRates)
      .symbolUnrealizedPnL(symbolUnrealizedPnL)
      .unpricedSymbols(unpricedSymbols)
      .build();

    log.info("포트폴리오 종합 정보: userId={}, 투자={}, 평가={}, 손익={}, 수익률={}%, 종목수={}",
      userId, totalInvested, totalCurrentValue, totalUnrealizedPnL,
      totalReturnRate, portfolio.size());

    // 포트폴리오 요약 조회 로깅
    tradeLogger.logPortfolioAccess(userId, "PORTFOLIO_SUMMARY",
      String.format("symbols:%d,totalValue:%s", portfolio.size(), totalCurrentValue),
      portfolio.size());

    return summary;
  }

}
