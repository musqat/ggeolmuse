package com.muscat.trade.domain.service.impl;

import com.muscat.trade.common.logging.TransactionLogger;
import com.muscat.trade.domain.dto.response.HoldingResponseDto;
import com.muscat.trade.domain.dto.response.PortfolioSummary;
import com.muscat.trade.domain.entity.Holdings;
import com.muscat.trade.domain.repository.HoldingsRepository;
import com.muscat.trade.domain.service.HoldingsService;
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
@Transactional
@Slf4j
public class HoldingsServiceImpl implements HoldingsService {

  private final HoldingsRepository holdingsRepository;
  private final TransactionLogger transactionLogger;

  @Override
  @Transactional(readOnly = true)
  public List<HoldingResponseDto> getPortfolio(String userId, String accountId) {
    List<Holdings> holdings;

    if (accountId != null) {
      log.debug("계좌별 포트폴리오 조회: userId={}, accountId={}", userId, accountId);
      holdings = holdingsRepository.findByUserIdAndAccountId(userId, accountId);
      transactionLogger.logPortfolioAccess(userId, "ACCOUNT_PORTFOLIO", accountId, holdings.size());
    } else {
      log.debug("사용자 전체 포트폴리오 조회: userId={}", userId);
      holdings = holdingsRepository.findByUserId(userId);
      transactionLogger.logPortfolioAccess(userId, "USER_PORTFOLIO", null, holdings.size());
    }

    return holdings.stream()
        .map(HoldingResponseDto::from)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public HoldingResponseDto getHoldingBySymbol(String userId, String accountId, String symbol) {
    Optional<Holdings> holding = holdingsRepository
        .findByUserIdAndAccountIdAndSymbol(userId, accountId, symbol);

    if (holding.isEmpty()) {
      log.warn("보유종목 없음: userId={}, accountId={}, symbol={}", userId, accountId, symbol);
      transactionLogger.logPortfolioAccess(userId, "SYMBOL_HOLDING", accountId + ":" + symbol, 0);
      return null;
    }

    transactionLogger.logPortfolioAccess(userId, "SYMBOL_HOLDING", accountId + ":" + symbol, 1);
    return HoldingResponseDto.from(holding.get());
  }


  @Override
  @Transactional(readOnly = true)
  public PortfolioSummary getPortfolioSummary(String userId,
      Map<String, BigDecimal> currentPrices) {
    log.debug("포트폴리오 종합 정보 계산: userId={}", userId);

    List<Holdings> portfolio = holdingsRepository.findByUserId(userId);

    // 기본 계산값들
    BigDecimal totalInvested = portfolio.stream()
        .map(Holdings::getTotalInvestedAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal totalCurrentValue = BigDecimal.ZERO;
    BigDecimal totalDividends = portfolio.stream()
        .map(Holdings::getTotalDividends)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

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
              .multiply(new BigDecimal("100"));
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
          .multiply(new BigDecimal("100"));
    }

    // 포트폴리오 요약 생성
    PortfolioSummary summary = new PortfolioSummary();
    summary.setTotalInvestedAmount(totalInvested);
    summary.setTotalCurrentValue(totalCurrentValue);
    summary.setTotalUnrealizedPnL(totalCurrentValue.subtract(totalInvested));
    summary.setTotalReturnRate(totalReturnRate);
    summary.setTotalDividends(totalDividends);
    summary.setHoldingCount(portfolio.size());
    summary.setHoldings(holdings);
    summary.setSymbolReturnRates(symbolReturnRates);
    summary.setSymbolUnrealizedPnL(symbolUnrealizedPnL);

    log.info("포트폴리오 종합 정보: userId={}, 투자={}, 평가={}, 손익={}, 수익률={}%, 배당={}, 종목수={}",
        userId, totalInvested, totalCurrentValue, totalCurrentValue.subtract(totalInvested),
        totalReturnRate, totalDividends, portfolio.size());

    // 포트폴리오 요약 조회 로깅
    transactionLogger.logPortfolioAccess(userId, "PORTFOLIO_SUMMARY", 
        String.format("symbols:%d,totalValue:%s", portfolio.size(), totalCurrentValue), portfolio.size());

    return summary;
  }


  @Override
  public void processDividend(String userId, String accountId, String symbol,
      BigDecimal dividendAmount) {
    Optional<Holdings> holdingOpt = holdingsRepository
        .findByUserIdAndAccountIdAndSymbol(userId, accountId, symbol);

    if (holdingOpt.isEmpty()) {
      log.warn("배당 처리 실패 - 보유종목 없음: userId={}, symbol={}", userId, symbol);
      return;
    }

    Holdings holding = holdingOpt.get();
    BigDecimal totalDividend = holding.getTotalQuantity().multiply(dividendAmount);

    holding.setTotalDividends(holding.getTotalDividends().add(totalDividend));

    // 배당 처리 로깅
    transactionLogger.logDividend(userId, symbol, holding.getTotalQuantity(), 
        dividendAmount, totalDividend, java.time.LocalDate.now());

    log.info("배당 지급 처리: userId={}, symbol={}, 주당배당={}, 총배당={}, 누적배당={}",
        userId, symbol, dividendAmount, totalDividend, holding.getTotalDividends());
  }
}