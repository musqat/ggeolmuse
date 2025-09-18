package com.muscat.trade.domain.service.impl;

import com.muscat.trade.common.constants.TradeConstants;
import com.muscat.trade.common.logging.TradeLogger;
import com.muscat.trade.domain.dto.response.HoldingResponseDto;
import com.muscat.trade.domain.dto.response.PortfolioSummary;
import com.muscat.trade.domain.entity.Holdings;
import com.muscat.trade.domain.repository.HoldingsQueryRepository;
import com.muscat.trade.domain.repository.HoldingsRepository;
import com.muscat.trade.domain.service.HoldingsService;
import com.muscat.trade.infra.client.BacktestServiceClient;
import com.muscat.trade.infra.client.dto.InvestmentBacktestResultDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class HoldingsServiceImpl implements HoldingsService {

  private final HoldingsRepository holdingsRepository;
  private final HoldingsQueryRepository holdingsQueryRepository;
  private final TradeLogger tradeLogger;
  private final BacktestServiceClient backtestServiceClient;

  @Override
  @Transactional(readOnly = true)
  public List<HoldingResponseDto> getPortfolio(String userId, String accountId) {
    log.debug("포트폴리오 조회: userId={}, accountId={}", userId, accountId);

    List<Holdings> holdings = accountId != null
        ? holdingsRepository.findByUserIdAndAccountId(userId, Long.valueOf(accountId))
        : holdingsRepository.findByUserId(userId);

    String logType = accountId != null ? "ACCOUNT_PORTFOLIO" : "USER_PORTFOLIO";
    tradeLogger.logPortfolioAccess(userId, logType, accountId, holdings.size());

    return holdings.stream()
        .map(HoldingResponseDto::from)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public HoldingResponseDto getHoldingBySymbol(String userId, String accountId, String symbol) {
    Optional<Holdings> holding = holdingsRepository.findByUserIdAndAccountIdAndSymbol(userId,
        Long.valueOf(accountId), symbol);

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

    var summaryProjection = holdingsQueryRepository.calculatePortfolioSummary(userId);
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
        holdings.add(HoldingResponseDto.fromWithCurrentPrice(holding, currentPrice));
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
        .backtestAvailable(false) // 기본값
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

  @Override
  @Transactional(readOnly = true)
  public PortfolioSummary getPortfolioSummaryWithBacktest(String userId,
      Map<String, BigDecimal> currentPrices, String authorization) {
    log.debug("백테스트 포함 포트폴리오 조회: userId={}", userId);

    // 1. 기본 포트폴리오 정보 조회
    PortfolioSummary summary = getPortfolioSummary(userId, currentPrices);

    // 2. 백테스트 결과 조회 (BacktestServiceClient 호출)
    try {
      Optional<InvestmentBacktestResultDto> backtestResult = getCachedBacktestResult(userId,
          authorization);

      if (backtestResult.isPresent()) {
        InvestmentBacktestResultDto result = backtestResult.get();

        // 백테스트 결과 설정
        summary.setBacktestAvailable(true);
        summary.setBacktestResult(result.getBacktestResult());
        summary.setBacktestCalculatedAt(result.getCalculatedAt());
        summary.setBacktestStatus(result.getStatus());

        log.info("백테스트 결과 포함 포트폴리오 조회 성공: userId={}, 백테스트계산일={}",
            userId, result.getCalculatedAt());
      } else {
        // 백테스트 결과 없음
        summary.setBacktestAvailable(false);
        log.debug("백테스트 결과 없음: userId={}", userId);
      }

    } catch (Exception e) {
      log.warn("백테스트 결과 조회 실패, 포트폴리오만 반환: userId={}, error={}", userId, e.getMessage());
      summary.setBacktestAvailable(false);
    }

    tradeLogger.logPortfolioAccess(userId, "PORTFOLIO_WITH_BACKTEST",
        String.format("symbols:%d,backtest:%s", summary.getHoldingCount(),
            summary.isBacktestAvailable()),
        summary.getHoldingCount());

    return summary;
  }

  private Optional<InvestmentBacktestResultDto> getCachedBacktestResult(String userId,
      String authorization) {
    try {
      log.debug("백테스트 결과 조회 시도 (로컬 캐시 우선): userId={}", userId);

      CompletableFuture<Optional<InvestmentBacktestResultDto>> future =
          CompletableFuture.supplyAsync(() -> {
            try {
              // 백테스트 서비스에서 로컬 캐시된 결과 조회
              InvestmentBacktestResultDto response =
                  backtestServiceClient.getCachedInvestmentBacktestResult(authorization, userId);

              if (response != null) {
                // 결과 유효성 확인 (24시간 이내 계산된 결과인지 체크)
                if (response.getCalculatedAt() != null &&
                    response.getCalculatedAt()
                        .isAfter(java.time.LocalDateTime.now().minusHours(24))) {
                  log.debug("유효한 백테스트 캐시 결과 조회 성공: userId={}, calculatedAt={}",
                      userId, response.getCalculatedAt());
                  return Optional.of(response);
                } else {
                  log.debug("백테스트 캐시 결과 만료됨: userId={}, calculatedAt={}",
                      userId, response.getCalculatedAt());
                }
              }

              log.debug("백테스트 캐시 결과 없음, 새로 계산 요청: userId={}", userId);
              return Optional.empty();

            } catch (Exception e) {
              log.warn("백테스트 서비스 호출 실패: userId={}, error={}", userId, e.getMessage());
              return Optional.empty();
            }
          });

      // 1초 타임아웃으로 단축 (로컬 DB 조회이므로 빠름)
      return future.get(1, TimeUnit.SECONDS);

    } catch (TimeoutException e) {
      log.warn("백테스트 캐시 조회 타임아웃 (1초): userId={}", userId);
      return Optional.empty();
    } catch (Exception e) {
      log.warn("백테스트 캐시 조회 중 예외 발생: userId={}, error={}", userId, e.getMessage());
      return Optional.empty();
    }
  }

  @Override
  @Transactional(readOnly = true)
  public List<HoldingResponseDto> getHoldingsWithTradeHistory(String userId, String symbol,
      Integer minQuantity) {
    log.debug("거래 이력 연관 보유종목 조회: userId={}, symbol={}, minQuantity={}", userId, symbol,
        minQuantity);

    List<Holdings> holdings = holdingsQueryRepository.findHoldingsWithTradeHistory(userId, symbol,
        minQuantity);

    tradeLogger.logPortfolioAccess(userId, "HOLDINGS_WITH_TRADE_HISTORY",
        String.format("symbol:%s,minQuantity:%s", symbol, minQuantity), holdings.size());

    return holdings.stream()
        .map(HoldingResponseDto::from)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public List<HoldingResponseDto> getTopPerformingHoldings(String userId, int limit) {
    log.debug("상위 수익률 종목 조회: userId={}, limit={}", userId, limit);

    List<Holdings> holdings = holdingsQueryRepository.findTopHoldingsByInvestment(userId, limit);

    tradeLogger.logPortfolioAccess(userId, "TOP_PERFORMING_HOLDINGS",
        String.format("limit:%d", limit), holdings.size());

    return holdings.stream()
        .map(HoldingResponseDto::from)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public List<HoldingResponseDto> getHoldingsByMinInvestment(String userId, BigDecimal minAmount) {
    log.debug("최소 투자금액 이상 종목 조회: userId={}, minAmount={}", userId, minAmount);

    List<Holdings> holdings = holdingsQueryRepository.findHoldingsByMinInvestment(userId,
        minAmount);

    tradeLogger.logPortfolioAccess(userId, "HOLDINGS_BY_MIN_INVESTMENT",
        String.format("minAmount:%s", minAmount), holdings.size());

    return holdings.stream()
        .map(HoldingResponseDto::from)
        .collect(Collectors.toList());
  }

}