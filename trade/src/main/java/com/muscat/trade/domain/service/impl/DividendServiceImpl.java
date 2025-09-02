package com.muscat.trade.domain.service.impl;

import com.muscat.trade.domain.entity.DividendHistory;
import com.muscat.trade.domain.entity.Holdings;
import com.muscat.trade.domain.repository.DividendHistoryRepository;
import com.muscat.trade.domain.repository.HoldingsRepository;
import com.muscat.trade.domain.service.DividendService;
import com.muscat.trade.domain.service.HoldingsService;
import com.muscat.trade.infra.client.MarketServiceClient;
import com.muscat.trade.infra.client.dto.DividendInfoDto;
import com.muscat.trade.common.exceptions.MarketDataException;
import com.muscat.trade.common.logging.TransactionLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class DividendServiceImpl implements DividendService {

  private final HoldingsRepository holdingsRepository;
  private final DividendHistoryRepository dividendHistoryRepository;
  private final MarketServiceClient marketServiceClient;
  private final TransactionLogger transactionLogger;
  private final HoldingsService holdingsService;

  // 매일 오전 9시에 배당 처리 실행
  @Override
  @Scheduled(cron = "0 0 9 * * *")
  public void processDailyDividends() {
    LocalDate today = LocalDate.now();
    log.info("일일 배당 처리 시작: date={}", today);

    try {
      // 배당 계산이 필요한 보유종목들 조회 (어제 배당 계산이 안된 종목들)
      List<Holdings> holdingsNeedingUpdate = holdingsRepository
          .findHoldingsNeedingDividendCalculation(today.minusDays(1));

      if (holdingsNeedingUpdate.isEmpty()) {
        log.info("배당 처리할 종목 없음: date={}", today);
        return;
      }

      // 종목별로 그룹화
      Map<String, List<Holdings>> holdingsBySymbol = holdingsNeedingUpdate.stream()
          .collect(Collectors.groupingBy(Holdings::getSymbol));

      int processedSymbols = 0;
      int processedHoldings = 0;

      for (String symbol : holdingsBySymbol.keySet()) {
        try {
          // market-data 모듈에서 배당 정보 조회
          BigDecimal dividendPerShare = getDividendDataFromMarketData(symbol, today);

          if (dividendPerShare != null && dividendPerShare.compareTo(BigDecimal.ZERO) > 0) {
            processDividendForSymbol(symbol, today, dividendPerShare);
            processedSymbols++;
            processedHoldings += holdingsBySymbol.get(symbol).size();
          } else {
            // 배당 없는 날도 날짜 업데이트
            updateLastDividendCalculatedDate(symbol, today);
          }

        } catch (Exception e) {
          log.error("종목 배당 처리 실패: symbol={}, error={}", symbol, e.getMessage(), e);
        }
      }

      log.info("일일 배당 처리 완료: date={}, processedSymbols={}, processedHoldings={}", 
          today, processedSymbols, processedHoldings);

    } catch (Exception e) {
      log.error("일일 배당 처리 중 오류 발생: date={}, error={}", today, e.getMessage(), e);
    }
  }

  @Override
  public void processDividendForSymbol(String symbol, LocalDate dividendDate, BigDecimal dividendPerShare) {
    log.info("종목별 배당 처리 시작: symbol={}, date={}, dividendPerShare={}", 
        symbol, dividendDate, dividendPerShare);

    // 해당 종목을 보유한 모든 사용자들 조회
    List<Holdings> holdings = holdingsRepository.findAll().stream()
        .filter(h -> h.getSymbol().equals(symbol) && h.getTotalQuantity().compareTo(BigDecimal.ZERO) > 0)
        .collect(Collectors.toList());

    int processedCount = 0;
    BigDecimal totalDividendPaid = BigDecimal.ZERO;

    for (Holdings holding : holdings) {
      try {
        processDividendForUser(holding.getUserId(), symbol, dividendDate, dividendPerShare);
        
        BigDecimal userDividend = holding.getTotalQuantity().multiply(dividendPerShare);
        totalDividendPaid = totalDividendPaid.add(userDividend);
        processedCount++;

      } catch (Exception e) {
        log.error("사용자 배당 처리 실패: userId={}, symbol={}, error={}", 
            holding.getUserId(), symbol, e.getMessage());
      }
    }

    log.info("종목별 배당 처리 완료: symbol={}, processedUsers={}, totalPaid={}", 
        symbol, processedCount, totalDividendPaid);
  }

  private void processDividendForUser(String userId, String symbol, LocalDate dividendDate, BigDecimal dividendPerShare) {
    // 사용자의 모든 계좌에서 해당 종목 조회
    List<Holdings> userHoldings = holdingsRepository.findByUserId(userId).stream()
        .filter(h -> h.getSymbol().equals(symbol) && h.getTotalQuantity().compareTo(BigDecimal.ZERO) > 0)
        .collect(Collectors.toList());

    for (Holdings holding : userHoldings) {
      try {
        // 배당 지급 처리
        holdingsService.processDividend(holding.getUserId(), holding.getAccountId(), symbol, dividendPerShare);
        
        // 배당 기록 저장
        BigDecimal totalDividend = holding.getTotalQuantity().multiply(dividendPerShare);
        DividendHistory dividendHistory = new DividendHistory(
            holding.getUserId(), holding.getAccountId(), symbol,
            holding.getTotalQuantity(), dividendPerShare, totalDividend,
            dividendDate, dividendDate
        );
        dividendHistoryRepository.save(dividendHistory);
        
        // 배당 로그 기록
        transactionLogger.logDividend(holding.getUserId(), symbol, holding.getTotalQuantity(), 
            dividendPerShare, totalDividend, dividendDate);
        
        // 마지막 배당 계산일 업데이트
        holding.setLastDividendCalculated(dividendDate);
        holdingsRepository.save(holding);

        log.debug("사용자 배당 처리: userId={}, accountId={}, symbol={}, quantity={}, dividend={}", 
            holding.getUserId(), holding.getAccountId(), symbol, 
            holding.getTotalQuantity(), holding.getTotalQuantity().multiply(dividendPerShare));
            
      } catch (Exception e) {
        log.error("개별 사용자 배당 처리 실패: userId={}, symbol={}, error={}", 
            holding.getUserId(), symbol, e.getMessage(), e);
      }
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Map<String, BigDecimal> getDividendSummary(String userId, int year) {
    Map<String, BigDecimal> summary = new HashMap<>();
    
    // Holdings에서 누적 배당금을 기반으로 요약 생성
    List<Holdings> holdings = holdingsRepository.findByUserId(userId);
    
    // 연간 총 배당금
    BigDecimal totalDividends = holdings.stream()
        .map(Holdings::getTotalDividends)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    
    // 종목별 배당금
    Map<String, BigDecimal> symbolDividends = holdings.stream()
        .filter(h -> h.getTotalDividends().compareTo(BigDecimal.ZERO) > 0)
        .collect(Collectors.toMap(
            Holdings::getSymbol,
            Holdings::getTotalDividends,
            BigDecimal::add
        ));
    
    summary.put("TOTAL", totalDividends);
    summary.putAll(symbolDividends);

    log.debug("배당 요약: userId={}, year={}, totalDividends={}, symbolCount={}", 
        userId, year, totalDividends, symbolDividends.size());
        
    return summary;
  }

  @Override
  @Transactional(readOnly = true)
  public List<DividendHistory> getDividendHistory(String userId) {
    return dividendHistoryRepository.findByUserIdOrderByDividendDateDesc(userId);
  }

  @Override
  @Transactional(readOnly = true)
  public List<DividendHistory> getDividendHistoryByYear(String userId, int year) {
    return dividendHistoryRepository.findByUserIdAndYear(userId, year);
  }

  @Override
  @Transactional(readOnly = true)
  public List<DividendHistory> getDividendHistoryBySymbol(String userId, String symbol) {
    return dividendHistoryRepository.findByUserIdAndSymbolOrderByDividendDateDesc(userId, symbol);
  }

  // ========== 내부 메서드들 ==========

  // market-data 모듈에서 배당 정보 조회
  private BigDecimal getDividendDataFromMarketData(String symbol, LocalDate date) {
    try {
      // market-data 모듈에서 해당 날짜의 배당 정보 조회
      var response = marketServiceClient.getDividendInfo(symbol);
      if (response.getData() != null) {
        DividendInfoDto dividendInfo = response.getData();
        
        // ex-dividend date가 조회 날짜와 일치하는 경우 배당금 반환
        if (dividendInfo.getExDividendDate() != null && 
            dividendInfo.getExDividendDate().equals(date) &&
            dividendInfo.getDividendAmount() != null) {
          
          transactionLogger.logMarketDataRequest(symbol, "DIVIDEND", true, null);
          log.info("배당 정보 발견: symbol={}, date={}, amount={}", 
              symbol, date, dividendInfo.getDividendAmount());
          return dividendInfo.getDividendAmount();
        }
      } else {
        transactionLogger.logMarketDataRequest(symbol, "DIVIDEND", false, response.getStatusMsg());
        log.warn("배당 정보 조회 실패: symbol={}, message={}", symbol, response.getStatusMsg());
      }
      
      log.debug("배당 없음: symbol={}, date={}", symbol, date);
      return BigDecimal.ZERO;
      
    } catch (Exception e) {
      transactionLogger.logMarketDataRequest(symbol, "DIVIDEND", false, e.getMessage());
      log.error("배당 정보 조회 실패: symbol={}, date={}, error={}", symbol, date, e.getMessage());
      
      // 중요한 배당 처리이므로 예외를 던지지 않고 0 반환 (서비스 중단 방지)
      return BigDecimal.ZERO;
    }
  }

  // 배당 없는 날도 마지막 계산일 업데이트
  private void updateLastDividendCalculatedDate(String symbol, LocalDate date) {
    List<Holdings> symbolHoldings = holdingsRepository.findAll().stream()
        .filter(h -> h.getSymbol().equals(symbol))
        .collect(Collectors.toList());

    for (Holdings holding : symbolHoldings) {
      holding.setLastDividendCalculated(date);
    }

    holdingsRepository.saveAll(symbolHoldings);
    log.debug("마지막 배당 계산일 업데이트: symbol={}, date={}, count={}", symbol, date, symbolHoldings.size());
  }
}