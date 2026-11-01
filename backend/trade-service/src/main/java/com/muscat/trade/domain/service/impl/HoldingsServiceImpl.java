package com.muscat.trade.domain.service.impl;

import com.muscat.commonlib.dto.StockPriceDto;
import com.muscat.trade.common.constants.TradeConstants;
import com.muscat.trade.common.logging.TradeLogger;
import com.muscat.trade.domain.dto.TradePosition;
import com.muscat.trade.domain.dto.response.HoldingResponseDto;
import com.muscat.trade.domain.dto.response.PortfolioSummary;
import com.muscat.trade.domain.entity.Holdings;
import com.muscat.trade.domain.repository.HoldingsRepository;
import com.muscat.trade.domain.service.DividendPayoutService;
import com.muscat.trade.domain.service.HoldingsService;
import com.muscat.trade.infra.client.MarketServiceClientWrapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

  @Override
  public List<HoldingResponseDto> getPortfolio(String userId, Long accountId) {
    log.debug("포트폴리오 조회: userId={}, accountId={}", userId, accountId);

    List<Holdings> holdings = accountId != null
      ? holdingsRepository.findByUserIdAndAccountId(userId, accountId)
      : holdingsRepository.findByUserId(userId);

    String logType = accountId != null ? "ACCOUNT_PORTFOLIO" : "USER_PORTFOLIO";
    tradeLogger.logPortfolioAccess(userId, logType, accountId != null ? accountId.toString() : null,
      holdings.size());

    // 보유 종목의 현재가를 한 번에(시세 서비스가 안 되면 대체 동작의 빈 맵 → 현재가 없이 반환)
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

    Map<TradePosition, BigDecimal> dividends = holdings.isEmpty()
      ? Map.of()
      : dividendPayoutService.dividendsOnHeldShares(userId);

    // 현재가 정보와 함께 DTO 생성
    return holdings.stream()
      .map(holding -> {
        BigDecimal currentPrice = currentPrices.get(holding.getSymbol());
        HoldingResponseDto dto = currentPrice != null
          ? HoldingResponseDto.of(holding, currentPrice)
          : HoldingResponseDto.from(holding);
        return dto.withTotalDividends(dividends.getOrDefault(
          new TradePosition(userId, holding.getAccountId(), holding.getSymbol()), BigDecimal.ZERO));
      })
      .collect(Collectors.toList());
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
  @Transactional(readOnly = true)
  public PortfolioSummary getPortfolioSummary(String userId,
    Map<String, BigDecimal> currentPrices) {
    log.debug("포트폴리오 종합 정보 계산: userId={}", userId);

    List<Holdings> portfolio = holdingsRepository.findByUserId(userId);

    var summaryProjection = holdingsRepository.calculatePortfolioSummary(userId);
    BigDecimal totalInvested =
      summaryProjection != null ? summaryProjection.totalInvestedAmount() : BigDecimal.ZERO;
    int holdingCount = summaryProjection != null ? summaryProjection.holdingCount() : 0;

    BigDecimal totalCurrentValue = BigDecimal.ZERO;

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

        // 종목별 수익률 계산
        if (holding.getAvgPurchasePrice().compareTo(BigDecimal.ZERO) > 0) {
          BigDecimal returnRate = currentPrice.subtract(holding.getAvgPurchasePrice())
            .divide(holding.getAvgPurchasePrice(), 4, RoundingMode.HALF_UP)
            .multiply(TradeConstants.PERCENTAGE_MULTIPLIER);
          symbolReturnRates.put(holding.getSymbol(), returnRate);
        }

        // 종목별 평가손익 계산
        BigDecimal bookValue = holding.getTotalQuantity().multiply(holding.getAvgPurchasePrice());
        BigDecimal pnl = holdingValue.subtract(bookValue);
        symbolUnrealizedPnL.put(holding.getSymbol(), pnl);

        // 현재가 포함한 보유 정보
        holdings.add(HoldingResponseDto.of(holding, currentPrice));
      } else {
        log.warn("현재가 정보 없음: symbol={}", holding.getSymbol());
        holdings.add(HoldingResponseDto.from(holding));
      }
    }

    // 총 수익률 계산
    BigDecimal totalReturnRate = BigDecimal.ZERO;
    if (totalInvested.compareTo(BigDecimal.ZERO) > 0) {
      totalReturnRate = totalCurrentValue.subtract(totalInvested)
        .divide(totalInvested, 4, RoundingMode.HALF_UP)
        .multiply(TradeConstants.PERCENTAGE_MULTIPLIER);
    }

    // 포트폴리오 요약 생성
    PortfolioSummary summary = PortfolioSummary.builder()
      .totalInvestedAmount(totalInvested)
      .totalCurrentValue(totalCurrentValue)
      .totalUnrealizedPnL(totalCurrentValue.subtract(totalInvested))
      .totalReturnRate(totalReturnRate)
      .holdingCount(holdingCount) // QueryDSL 결과 사용
      .holdings(holdings)
      .symbolReturnRates(symbolReturnRates)
      .symbolUnrealizedPnL(symbolUnrealizedPnL)
      .build();

    log.info("포트폴리오 종합 정보: userId={}, 투자={}, 평가={}, 손익={}, 수익률={}%, 종목수={}",
      userId, totalInvested, totalCurrentValue, totalCurrentValue.subtract(totalInvested),
      totalReturnRate, portfolio.size());

    // 포트폴리오 요약 조회 로깅
    tradeLogger.logPortfolioAccess(userId, "PORTFOLIO_SUMMARY",
      String.format("symbols:%d,totalValue:%s", portfolio.size(), totalCurrentValue),
      portfolio.size());

    return summary;
  }

}
