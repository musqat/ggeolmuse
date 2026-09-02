package com.muscat.backtest.common.util;

import com.muscat.backtest.common.enums.BacktestResponse;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.infra.client.MarketDataClient;
import com.muscat.backtest.infra.client.dto.DividendHistoryDto;
import com.muscat.backtest.infra.client.dto.FxRateDto;
import com.muscat.commonlib.dto.OHLCPriceDto;
import com.muscat.commonlib.dto.StockPriceDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

// 백테스팅 데이터 처리 유틸리티 클래스
@Slf4j
public final class BacktestDataUtils {

  private BacktestDataUtils() {
    throw new AssertionError("유틸리티 클래스는 인스턴스화할 수 없습니다");
  }

  /**
   * 특정 날짜의 과거 주가 데이터 조회 (시장 휴일 대응)
   */
  public static OHLCPriceDto getHistoricalPrice(MarketDataClient marketDataClient, String symbol,
                                                LocalDate date) {
    log.info("주가 데이터 요청: symbol={}, date={}", symbol, date);
    return PriceLookup.withFallback(marketDataClient::getOHLCPrice, symbol, date, 5);
  }

  /**
   * 현재 주가 데이터 조회
   */
  public static StockPriceDto getCurrentPrice(MarketDataClient marketDataClient, String symbol) {
    var response = marketDataClient.getCurrentPrice(symbol);

    if (response == null || !response.available()) {
      throw new BacktestException(BacktestResponse.STOCK_DATA_NOT_FOUND,
        "현재 주가 데이터를 찾을 수 없습니다: " + symbol);
    }

    return response;
  }


  /**
   * 특정 날짜의 환율 데이터 조회 (기본값 fallback)
   */
  public static FxRateDto getHistoricalFxRate(MarketDataClient marketDataClient, LocalDate date) {
    try {
      var response = marketDataClient.getFxRate(date.toString());

      if (response == null) {
        log.warn("환율 데이터가 없어 기본값 사용: {} -> {}원", date, FxFallback.DEFAULT_RATE);
        return FxFallback.defaultFxRate(date);
      }

      return response;
    } catch (Exception e) {
      log.warn("환율 데이터 조회 실패, 기본값 사용: {} -> {}원", date, FxFallback.DEFAULT_RATE);
      return FxFallback.defaultFxRate(date);
    }
  }

  /**
   * 현재 환율 데이터 조회 (기본값 fallback)
   */
  public static FxRateDto getCurrentFxRate(MarketDataClient marketDataClient) {
    try {
      var response = marketDataClient.getLatestFxRate();

      if (response == null) {
        log.warn("현재 환율 데이터가 없어 기본값 사용: {}원", FxFallback.DEFAULT_RATE);
        return FxFallback.defaultFxRate(LocalDate.now());
      }

      return response;
    } catch (Exception e) {
      log.warn("현재 환율 데이터 조회 실패, 기본값 사용: {}원", FxFallback.DEFAULT_RATE);
      return FxFallback.defaultFxRate(LocalDate.now());
    }
  }

  /**
   *  특정 기간의 배당 이력 조회
   */
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

      // List<DividendDto>를 DividendHistoryDto로 변환
      DividendHistoryDto history = new DividendHistoryDto();
      history.setSymbol(symbol);

      // DividendDto를 DividendPayment로 변환
      var payments = response.stream()
        .map(dto -> {
          var payment = new DividendHistoryDto.DividendPayment();
          payment.setExDate(dto.exDate());
          payment.setPayDate(dto.paymentDate());
          payment.setAmount(dto.amount());
          payment.setFrequency(null); // frequency는 API에서 제공하지 않음
          return payment;
        })
        .toList();

      history.setDividends(payments);
      log.info("배당 데이터 변환 완료: symbol={}, dividends={}", symbol, payments.size());

      return history;

    } catch (Exception e) {
      log.warn("배당 데이터 조회 중 오류 발생: symbol={}, error={}", symbol, e.getMessage());
      DividendHistoryDto emptyHistory = new DividendHistoryDto();
      emptyHistory.setSymbol(symbol);
      emptyHistory.setDividends(java.util.Collections.emptyList());
      return emptyHistory;
    }
  }

  /**
   * 전체 기간 OHLC를 BULK 조회해 날짜→가격 Map으로 구성(available만, 중복 날짜는 뒤 값 우선).
   * DCA/조건부/최적타이밍 3개 전략이 복붙하던 priceMap 빌드 블록을 단일화.
   */
  public static java.util.Map<LocalDate, OHLCPriceDto> buildPriceMap(
    MarketDataClient marketDataClient, String symbol, LocalDate startDate, LocalDate endDate) {
    java.util.List<OHLCPriceDto> allPrices = marketDataClient.getOHLCPriceRange(
      symbol, startDate.toString(), endDate.toString());

    java.util.Map<LocalDate, OHLCPriceDto> priceMap = allPrices.stream()
      .filter(OHLCPriceDto::available)
      .collect(java.util.stream.Collectors.toMap(
        OHLCPriceDto::date, p -> p, (existing, replacement) -> replacement));

    log.info("가격 데이터 조회 완료: {}개", priceMap.size());
    return priceMap;
  }

  /**
   * start~end 일 단위 전체 날짜의 환율을 BULK 조회해 Map으로 구성.
   * 조건부/최적타이밍이 복붙하던 일별 fxRateMap 빌드 블록을 단일화(DCA는 월별이라 별도).
   */
  public static Map<LocalDate, BigDecimal> buildDailyFxRateMap(
    MarketDataClient marketDataClient, LocalDate startDate, LocalDate endDate) {
    List<LocalDate> allDates = new java.util.ArrayList<>();
    for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
      allDates.add(d);
    }
    return getBulkFxRates(marketDataClient, allDates);
  }

  /**
   *  Bulk 환율 조회 (여러 날짜의 환율을 한 번에)
   */
  public static java.util.Map<LocalDate, BigDecimal> getBulkFxRates(
    MarketDataClient marketDataClient, java.util.List<LocalDate> dates) {
    log.info("Bulk 환율 데이터 요청: dates count={}", dates.size());

    try {
      var dateStrings = dates.stream()
        .map(LocalDate::toString)
        .toList();

      var response = marketDataClient.getBulkFxRates(dateStrings);

      // String key를 LocalDate로 변환
      java.util.Map<LocalDate, BigDecimal> result = new java.util.HashMap<>();
      for (var entry : response.entrySet()) {
        LocalDate date = LocalDate.parse(entry.getKey());
        result.put(date, entry.getValue());
      }

      // 응답에 없는 날짜는 기본 환율로 채움 (FX 데이터 미수집 환경에서도 전략 백테스트가 동작하도록)
      for (LocalDate date : dates) {
        result.putIfAbsent(date, FxFallback.DEFAULT_RATE);
      }

      log.info("Bulk 환율 조회 성공: {}개 요청, {}개 반환(기본값 보정 포함)", dates.size(), result.size());
      return result;

    } catch (Exception e) {
      log.warn("Bulk 환율 조회 실패, 기본값 사용: {}", e.getMessage());
      // 실패 시 기본 환율로 채우기
      java.util.Map<LocalDate, BigDecimal> result = new java.util.HashMap<>();
      for (LocalDate date : dates) {
        result.put(date, FxFallback.DEFAULT_RATE);
      }
      return result;
    }
  }
}
