package com.muscat.marketdata.domain.service.impl;


import com.muscat.marketdata.config.MarketDataProperties;
import com.muscat.marketdata.domain.dto.DividendDto;
import com.muscat.marketdata.domain.dto.OHLCPriceDto;
import com.muscat.marketdata.domain.dto.StockPriceDto;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.entity.Candle;
import com.muscat.marketdata.domain.entity.Dividend;
import com.muscat.marketdata.domain.repository.AssetQueryRepository;
import com.muscat.marketdata.domain.repository.CandleQueryRepository;
import com.muscat.marketdata.domain.repository.CandleRepository;
import com.muscat.marketdata.domain.repository.DividendQueryRepository;
import com.muscat.marketdata.domain.repository.DividendRepository;
import com.muscat.marketdata.domain.service.MarketService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketServiceImpl implements MarketService {

  private final CandleRepository candleRepository;
  private final CandleQueryRepository candleQueryRepository;
  private final DividendRepository dividendRepository;
  private final DividendQueryRepository dividendQueryRepository;
  private final AssetQueryRepository assetQueryRepository;
  private final MarketDataProperties properties;

  @Override
  public OHLCPriceDto getOHLCPrice(String symbol, LocalDate date) {
    log.debug("OHLC 조회 요청: symbol={}, date={}", symbol, date);

    // 심볼 대문자 변환 및 DB 조회
    String upperSymbol = symbol.toUpperCase();
    Optional<Candle> candle = candleRepository.findBySymbolAndDate(upperSymbol, date);

    // 데이터 없을 경우 비어있는 DTO 반환
    if (candle.isEmpty()) {
      log.warn("OHLC 데이터 없음: symbol={}, date={}", symbol, date);
      return OHLCPriceDto.builder()
          .symbol(upperSymbol)
          .date(date)
          .available(false)
          .build();
    }

    // 캔들 데이터를 DTO로 변환
    Candle c = candle.get();
    OHLCPriceDto result = OHLCPriceDto.builder()
        .symbol(c.getSymbol())
        .date(c.getDate())
        .openPrice(c.getOpen())
        .highPrice(c.getHigh())
        .lowPrice(c.getLow())
        .closePrice(c.getClose())
        .adjustedClose(c.getAdjustedClose())
        .volume(c.getVolume())
        .currency(c.getCurrency())
        .available(true)
        .build();

    log.debug("OHLC 조회 성공: symbol={}, date={}, close={}", symbol, date, c.getClose());
    return result;
  }

  @Override
  public StockPriceDto getCurrentPrice(String symbol) {
    log.debug("현재가 조회 요청: symbol={}", symbol);

    // 최신 캔들 데이터 조회
    String upperSymbol = symbol.toUpperCase();
    Optional<Candle> latestCandle = candleRepository.findFirstBySymbolOrderByDateDesc(upperSymbol);

    if (latestCandle.isEmpty()) {
      log.warn("현재가 데이터 없음: symbol={}", symbol);
      return StockPriceDto.builder()
          .symbol(upperSymbol)
          .available(false)
          .build();
    }

    Candle c = latestCandle.get();

    // 전일 데이터 조회 및 변화율 계산
    Optional<Candle> previousCandle = candleRepository
        .findFirstBySymbolAndDateLessThanOrderByDateDesc(upperSymbol, c.getDate());

    BigDecimal previousClose = previousCandle.map(Candle::getClose).orElse(c.getClose());
    BigDecimal changePercent = calculateChangePercent(c.getClose(), previousClose);

    // 주가 정보 DTO 생성
    StockPriceDto result = StockPriceDto.builder()
        .symbol(c.getSymbol())
        .date(c.getDate())
        .currentPrice(c.getClose())
        .previousClose(previousClose)
        .changePercent(changePercent)
        .volume(c.getVolume())
        .currency(c.getCurrency())
        .available(true)
        .build();

    log.debug("현재가 조회 성공: symbol={}, price={}, previousClose={}, change={}%",
        symbol, c.getClose(), previousClose, changePercent);
    return result;
  }

  @Override
  public List<DividendDto> getDividendHistory(String symbol, LocalDate startDate,
      LocalDate endDate) {
    log.debug("배당 이력 조회 요청: symbol={}, startDate={}, endDate={}", symbol, startDate, endDate);

    // 지정된 기간의 배당 데이터 조회
    String upperSymbol = symbol.toUpperCase();
    List<Dividend> dividends = dividendQueryRepository.findBySymbolsAndDateRange(
        List.of(upperSymbol), startDate, endDate);

    // 엔티티를 DTO로 변환
    List<DividendDto> result = dividends.stream()
        .map(dividend -> DividendDto.builder()
            .symbol(dividend.getSymbol())
            .exDate(dividend.getExDate())
            .amount(dividend.getAmount())
            .currency(dividend.getCurrency())
            .source("MarketData")
            .build())
        .collect(Collectors.toList());

    log.debug("배당 이력 조회 성공: symbol={}, count={}", symbol, result.size());
    return result;
  }

  // 여러 종목의 OHLC 데이터 일괄 조회
  public List<OHLCPriceDto> getMultipleOHLCPrices(List<String> symbols, LocalDate startDate,
      LocalDate endDate) {
    log.debug("다중 OHLC 조회 요청: symbols={}, startDate={}, endDate={}", symbols, startDate, endDate);

    // 심볼 대문자 변환 및 범위 조회
    List<String> upperSymbols = symbols.stream().map(String::toUpperCase).toList();
    List<Candle> candles = candleQueryRepository.findBySymbolsAndDateRange(upperSymbols, startDate,
        endDate);

    // 캔들 데이터 DTO 변환
    List<OHLCPriceDto> result = candles.stream()
        .map(c -> OHLCPriceDto.builder()
            .symbol(c.getSymbol())
            .date(c.getDate())
            .openPrice(c.getOpen())
            .highPrice(c.getHigh())
            .lowPrice(c.getLow())
            .closePrice(c.getClose())
            .adjustedClose(c.getAdjustedClose())
            .volume(c.getVolume())
            .currency(c.getCurrency())
            .available(true)
            .build())
        .toList();

    log.debug("다중 OHLC 조회 성공: count={}", result.size());
    return result;
  }

  // 배당 지급일의 캔들 데이터 조회
  public List<OHLCPriceDto> getCandlesWithDividends(String symbol, LocalDate startDate,
      LocalDate endDate) {
    log.debug("배당 포함 캔들 조회 요청: symbol={}, startDate={}, endDate={}", symbol, startDate, endDate);

    // 배당이 있는 날짜의 캔들 데이터 조회
    String upperSymbol = symbol.toUpperCase();
    List<Candle> candlesWithDividends = candleQueryRepository.findCandlesWithDividends(upperSymbol,
        startDate, endDate);

    // 캔들 데이터 DTO 변환
    List<OHLCPriceDto> result = candlesWithDividends.stream()
        .map(c -> OHLCPriceDto.builder()
            .symbol(c.getSymbol())
            .date(c.getDate())
            .openPrice(c.getOpen())
            .highPrice(c.getHigh())
            .lowPrice(c.getLow())
            .closePrice(c.getClose())
            .adjustedClose(c.getAdjustedClose())
            .volume(c.getVolume())
            .currency(c.getCurrency())
            .available(true)
            .build())
        .toList();

    log.debug("배당 포함 캔들 조회 성공: count={}", result.size());
    return result;
  }

  // 고배당 주식 검색
  public List<DividendDto> findHighDividendStocks(BigDecimal minAmount, LocalDate fromDate) {
    log.debug("고배당주 검색 요청: minAmount={}, fromDate={}", minAmount, fromDate);

    // 최소 금액 이상의 배당 데이터 검색
    List<Dividend> highDividends = dividendQueryRepository.findHighDividendStocks(minAmount,
        fromDate);

    // 배당 데이터 DTO 변환
    List<DividendDto> result = highDividends.stream()
        .map(dividend -> DividendDto.builder()
            .symbol(dividend.getSymbol())
            .exDate(dividend.getExDate())
            .amount(dividend.getAmount())
            .currency(dividend.getCurrency())
            .source("MarketData")
            .build())
        .toList();

    log.debug("고배당주 검색 성공: count={}", result.size());
    return result;
  }

  @Override
  public List<String> getAllSymbols() {
    log.debug("전체 심볼 목록 조회 요청");

    List<String> symbols = assetQueryRepository.findAllSymbols();

    log.debug("전체 심볼 목록 조회 성공: count={}", symbols.size());
    return symbols;
  }

  @Override
  public List<Asset> getAssetsByCountry(String country) {
    log.debug("국가별 자산 조회 요청: country={}", country);

    List<Asset> assets = assetQueryRepository.findByCountry(country);

    log.debug("국가별 자산 조회 성공: country={}, count={}", country, assets.size());
    return assets;
  }

  @Override
  public List<Asset> getAssetsByCurrency(String currency) {
    log.debug("통화별 자산 조회 요청: currency={}", currency);

    List<Asset> assets = assetQueryRepository.findByCurrency(currency);

    log.debug("통화별 자산 조회 성공: currency={}, count={}", currency, assets.size());
    return assets;
  }

  @Override
  public List<Asset> getAssetsByType(String assetType) {
    log.debug("자산유형별 조회 요청: assetType={}", assetType);

    List<Asset> assets = assetQueryRepository.findByAssetType(assetType);

    log.debug("자산유형별 조회 성공: assetType={}, count={}", assetType, assets.size());
    return assets;
  }

  @Override
  public List<Asset> getAssetsWithFilters(String country, String currency, String assetType) {
    log.debug("동적 필터링 자산 조회 요청: country={}, currency={}, assetType={}",
        country, currency, assetType);

    List<Asset> assets = assetQueryRepository.findWithDynamicFilters(country, currency, assetType);

    log.debug("동적 필터링 자산 조회 성공: count={}", assets.size());
    return assets;
  }

  // 변화율 계산 메서드
  private BigDecimal calculateChangePercent(BigDecimal currentPrice, BigDecimal previousClose) {
    if (currentPrice == null || previousClose == null ||
        previousClose.compareTo(BigDecimal.ZERO) == 0) {
      return null;
    }

    return currentPrice.subtract(previousClose)
        .divide(previousClose, properties.getCalculation().getPercentScale(), RoundingMode.HALF_UP)
        .multiply(properties.getCalculation().getPercentageMultiplier());
  }
}