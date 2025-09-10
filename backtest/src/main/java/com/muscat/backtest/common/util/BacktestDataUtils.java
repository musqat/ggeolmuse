package com.muscat.backtest.common.util;

import com.muscat.backtest.common.enums.BacktestResponse;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.infra.client.MarketDataClient;
import com.muscat.backtest.infra.client.MarketDataClient.FxRate;
import com.muscat.backtest.infra.client.dto.DividendHistoryDto;
import com.muscat.backtest.infra.client.dto.OHLCPriceDto;
import com.muscat.backtest.infra.client.dto.StockPriceDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;

// 백테스팅 데이터 처리 유틸리티 클래스
@Slf4j
public final class BacktestDataUtils {

  private BacktestDataUtils() {
    throw new AssertionError("유틸리티 클래스는 인스턴스화할 수 없습니다");
  }

  // 기본 환율 상수
  private static final BigDecimal DEFAULT_FX_RATE_HISTORICAL = new BigDecimal("1350");
  private static final BigDecimal DEFAULT_FX_RATE_CURRENT = new BigDecimal("1380");


  // 특정 날짜의 과거 주가 데이터 조회
  public static OHLCPriceDto getHistoricalPrice(MarketDataClient marketDataClient, String symbol,
      LocalDate date) {
    log.info("주가 데이터 요청: symbol={}, date={}", symbol, date);

    var response = marketDataClient.getOHLCPrice(symbol, date.toString());
    log.info("주가 데이터 응답: data={}", response);

    if (response == null || !response.isAvailable()) {
      throw new BacktestException(BacktestResponse.STOCK_DATA_NOT_FOUND,
          "매수일의 주가 데이터를 찾을 수 없습니다: " + symbol + ", " + date);
    }

    return response;
  }

  // 현재 주가 데이터 조회
  public static StockPriceDto getCurrentPrice(MarketDataClient marketDataClient, String symbol) {
    var response = marketDataClient.getCurrentPrice(symbol);

    if (response == null || !response.isAvailable()) {
      throw new BacktestException(BacktestResponse.STOCK_DATA_NOT_FOUND,
          "현재 주가 데이터를 찾을 수 없습니다: " + symbol);
    }

    return response;
  }


  // 특정 날짜의 환율 데이터 조회 (기본값 fallback)
  public static FxRate getHistoricalFxRate(MarketDataClient marketDataClient, LocalDate date) {
    try {
      var response = marketDataClient.getFxRate(date.toString());

      if (response == null) {
        log.warn("환율 데이터가 없어 기본값 사용: {} -> {}원", date, DEFAULT_FX_RATE_HISTORICAL);
        return new FxRate(date, DEFAULT_FX_RATE_HISTORICAL);
      }

      return response;
    } catch (Exception e) {
      log.warn("환율 데이터 조회 실패, 기본값 사용: {} -> {}원", date, DEFAULT_FX_RATE_HISTORICAL);
      return new FxRate(date, DEFAULT_FX_RATE_HISTORICAL);
    }
  }

  // 현재 환율 데이터 조회 (기본값 fallback)
  public static FxRate getCurrentFxRate(MarketDataClient marketDataClient) {
    try {
      var response = marketDataClient.getLatestFxRate();

      if (response == null) {
        log.warn("현재 환율 데이터가 없어 기본값 사용: {}원", DEFAULT_FX_RATE_CURRENT);
        return new FxRate(LocalDate.now(), DEFAULT_FX_RATE_CURRENT);
      }

      return response;
    } catch (Exception e) {
      log.warn("현재 환율 데이터 조회 실패, 기본값 사용: {}원", DEFAULT_FX_RATE_CURRENT);
      return new FxRate(LocalDate.now(), DEFAULT_FX_RATE_CURRENT);
    }
  }

  // 특정 기간의 배당 이력 조회
  public static DividendHistoryDto getDividendHistory(MarketDataClient marketDataClient,
      String symbol, LocalDate startDate, LocalDate endDate) {
    log.info("배당 데이터 요청: symbol={}, startDate={}, endDate={}", symbol, startDate, endDate);

    try {
      var response = marketDataClient.getDividendHistory(symbol, startDate.toString(),
          endDate.toString());

      if (response == null || response.isEmpty()) {
        log.warn("배당 데이터를 찾을 수 없습니다: {}", symbol);
        DividendHistoryDto emptyHistory = new DividendHistoryDto();
        emptyHistory.setSymbol(symbol);
        emptyHistory.setDividends(java.util.Collections.emptyList());
        return emptyHistory;
      }

      log.info("배당 데이터 조회 성공: symbol={}, count={}", symbol, response.size());
      
      return response.get(0);

    } catch (Exception e) {
      log.warn("배당 데이터 조회 중 오류 발생: symbol={}, error={}", symbol, e.getMessage());
      DividendHistoryDto emptyHistory = new DividendHistoryDto();
      emptyHistory.setSymbol(symbol);
      emptyHistory.setDividends(java.util.Collections.emptyList());
      return emptyHistory;
    }
  }
}