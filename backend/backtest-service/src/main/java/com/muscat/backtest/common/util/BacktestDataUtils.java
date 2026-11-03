package com.muscat.backtest.common.util;

import com.muscat.backtest.common.enums.response.BacktestResponse;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.infra.client.MarketDataClientWrapper;
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
  public static OHLCPriceDto getHistoricalPrice(MarketDataClientWrapper marketData, String symbol,
                                                LocalDate date) {
    log.info("주가 데이터 요청: symbol={}, date={}", symbol, date);
    return PriceLookup.withFallback(marketData::getOHLCPrice, symbol, date, 5);
  }

  /**
   * 현재 주가 데이터 조회
   */
  public static StockPriceDto getCurrentPrice(MarketDataClientWrapper marketData, String symbol) {
    var response = marketData.getCurrentPrice(symbol);

    if (response == null || !response.available()) {
      throw new BacktestException(BacktestResponse.STOCK_DATA_NOT_FOUND,
        "현재 주가 데이터를 찾을 수 없습니다: " + symbol);
    }

    return response;
  }


  /**
   * 평가일 시세(오늘 이후면 현재가, 과거면 그 날 종가)
   */
  public static StockPriceDto getPriceAt(MarketDataClientWrapper marketData, String symbol,
                                         LocalDate date) {
    if (!date.isBefore(LocalDate.now())) {
      return getCurrentPrice(marketData, symbol);
    }

    OHLCPriceDto ohlc = getHistoricalPrice(marketData, symbol, date);
    return new StockPriceDto(symbol, null, PriceLookup.effectiveClose(ohlc), null, null, null,
      ohlc.volume(), ohlc.date(), ohlc.date().atStartOfDay(), ohlc.openPrice(), ohlc.highPrice(),
      ohlc.lowPrice(), ohlc.closePrice(), ohlc.adjustedClose(), ohlc.currency(), true, null, null);
  }

  /**
   * 평가일 환율(오늘 이후면 최신, 과거면 그 날 환율)
   */
  public static FxRateDto getFxRateAt(MarketDataClientWrapper marketData, LocalDate date) {
    return date.isBefore(LocalDate.now())
      ? getHistoricalFxRate(marketData, date)
      : getCurrentFxRate(marketData);
  }

  /**
   * 특정 날짜의 환율 데이터 조회 (기본값 fallback)
   */
  public static FxRateDto getHistoricalFxRate(MarketDataClientWrapper marketData, LocalDate date) {
    var response = marketData.getFxRate(date.toString());
    if (response == null) {
      log.warn("환율 데이터가 없어 기본값 사용: {} -> {}원", date, marketData.defaultFxRate());
      return new FxRateDto(date, marketData.defaultFxRate());
    }
    return response;
  }

  /**
   * 현재 환율 데이터 조회 (기본값 fallback)
   */
  public static FxRateDto getCurrentFxRate(MarketDataClientWrapper marketData) {
    var response = marketData.getLatestFxRate();
    if (response == null) {
      log.warn("현재 환율 데이터가 없어 기본값 사용: {}원", marketData.defaultFxRate());
      return new FxRateDto(LocalDate.now(), marketData.defaultFxRate());
    }
    return response;
  }

  /**
   *  특정 기간의 배당 이력 조회
   */
  public static DividendHistoryDto getDividendHistory(MarketDataClientWrapper marketData,
                                                      String symbol, LocalDate startDate, LocalDate endDate) {
    log.info("배당 데이터 요청: symbol={}, startDate={}, endDate={}", symbol, startDate, endDate);

    var response = marketData.getDividendHistory(symbol, startDate.toString(), endDate.toString());

    DividendHistoryDto history = new DividendHistoryDto();
    history.setSymbol(symbol);
    if (response == null || response.isEmpty()) {
      log.warn("배당 데이터를 찾을 수 없습니다: {}", symbol);
      history.setDividends(java.util.Collections.emptyList());
      return history;
    }

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
  }

  // 기간 OHLC 를 한 번에 받아 날짜 → 가격 맵(available 만, 같은 날짜는 뒤 값)
  public static java.util.Map<LocalDate, OHLCPriceDto> buildPriceMap(
    MarketDataClientWrapper marketData, String symbol, LocalDate startDate, LocalDate endDate) {
    java.util.List<OHLCPriceDto> allPrices = marketData.getOHLCPriceRange(
      symbol, startDate.toString(), endDate.toString());

    java.util.Map<LocalDate, OHLCPriceDto> priceMap = allPrices.stream()
      .filter(OHLCPriceDto::available)
      .collect(java.util.stream.Collectors.toMap(
        OHLCPriceDto::date, p -> p, (existing, replacement) -> replacement));

    log.info("가격 데이터 조회 완료: {}개", priceMap.size());
    return priceMap;
  }

  // start ~ end 의 날마다 환율을 한 번에 받아 맵으로(DCA 는 월별이라 따로)
  public static Map<LocalDate, BigDecimal> buildDailyFxRateMap(
    MarketDataClientWrapper marketData, LocalDate startDate, LocalDate endDate) {
    List<LocalDate> allDates = new java.util.ArrayList<>();
    for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
      allDates.add(d);
    }
    return getBulkFxRates(marketData, allDates);
  }

  /**
   *  Bulk 환율 조회 (여러 날짜의 환율을 한 번에)
   */
  public static java.util.Map<LocalDate, BigDecimal> getBulkFxRates(
    MarketDataClientWrapper marketData, java.util.List<LocalDate> dates) {
    log.info("Bulk 환율 데이터 요청: dates count={}", dates.size());

    var response = marketData.getBulkFxRates(dates.stream().map(LocalDate::toString).toList());

    // String key를 LocalDate로 변환
    java.util.Map<LocalDate, BigDecimal> result = new java.util.HashMap<>();
    for (var entry : response.entrySet()) {
      result.put(LocalDate.parse(entry.getKey()), entry.getValue());
    }

    // 응답에 없는 날짜는 기본 환율
    for (LocalDate date : dates) {
      result.putIfAbsent(date, marketData.defaultFxRate());
    }

    log.info("Bulk 환율 조회: {}개 요청, {}개 반환(기본값 보정 포함)", dates.size(), result.size());
    return result;
  }
}
