package com.muscat.marketdata.domain.service.impl;

import com.muscat.marketdata.config.MarketDataProperties;
import com.muscat.marketdata.domain.dto.OHLCPriceDto;
import com.muscat.marketdata.domain.dto.StockPriceDto;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.entity.Candle;
import com.muscat.marketdata.domain.repository.AssetRepository;
import com.muscat.marketdata.domain.repository.CandleRepository;
import com.muscat.marketdata.domain.service.CandleService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Candle(캔들) 데이터 조회 서비스 구현체
 * OHLC 가격 데이터 및 주식 현재가 조회를 담당합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CandleServiceImpl implements CandleService {

  private final CandleRepository candleRepository;
  private final AssetRepository assetRepository;
  private final MarketDataProperties properties;
  private final org.springframework.cache.CacheManager cacheManager;

  @Override
  @Cacheable(cacheNames = "ohlc", key = "#symbol.toUpperCase() + ':' + #date")
  public OHLCPriceDto getOHLCPrice(String symbol, LocalDate date) {
    log.debug("OHLC 조회 요청: symbol={}, date={}", symbol, date);

    // 종목코드 대문자 변환 및 DB 조회
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

    // 캔들 데이터를 DTO로 변환 (adjustedClose 비율로 모든 OHLC 조정)
    Candle c = candle.get();

    // adjustedClose 비율 계산 (액면분할/배당 반영)
    BigDecimal ratio = BigDecimal.ONE;
    if (c.getClose() != null && c.getClose().compareTo(BigDecimal.ZERO) > 0
      && c.getAdjustedClose() != null) {
      ratio = c.getAdjustedClose().divide(c.getClose(), 8, java.math.RoundingMode.HALF_UP);
    }

    OHLCPriceDto result = OHLCPriceDto.builder()
      .symbol(c.getSymbol())
      .date(c.getDate())
      .openPrice(c.getOpen() != null ? c.getOpen().multiply(ratio) : null)
      .highPrice(c.getHigh() != null ? c.getHigh().multiply(ratio) : null)
      .lowPrice(c.getLow() != null ? c.getLow().multiply(ratio) : null)
      .closePrice(c.getClose() != null ? c.getClose().multiply(ratio) : null)
      .adjustedClose(c.getAdjustedClose())
      .volume(c.getVolume())
      .currency(c.getCurrency())
      .available(true)
      .build();

    log.debug("OHLC 조회 성공: symbol={}, date={}, close={}", symbol, date, c.getClose());
    return result;
  }

  @Override
  public List<OHLCPriceDto> getOHLCPriceRange(String symbol, LocalDate startDate,
    LocalDate endDate) {
    log.debug("OHLC 범위 조회 요청: symbol={}, startDate={}, endDate={}", symbol, startDate, endDate);

    String upperSymbol = symbol.toUpperCase();
    List<OHLCPriceDto> result = new ArrayList<>();
    List<LocalDate> cacheMissDates = new ArrayList<>();

    // 1. 날짜별로 개별 캐시 조회 (기존 @Cacheable getOHLCPrice 재사용)
    LocalDate current = startDate;
    while (!current.isAfter(endDate)) {
      OHLCPriceDto cached = getOHLCPrice(upperSymbol, current);

      if (cached != null && cached.isAvailable()) {
        result.add(cached);  // Cache Hit
      } else {
        cacheMissDates.add(current);  // Cache Miss
      }

      current = current.plusDays(1);
    }

    // 2. Cache Miss 날짜들만 DB Batch 조회
    if (!cacheMissDates.isEmpty()) {
      LocalDate batchStart = cacheMissDates.get(0);
      LocalDate batchEnd = cacheMissDates.get(cacheMissDates.size() - 1);

      List<Candle> candles = candleRepository
        .findBySymbolAndDateBetweenOrderByDateAsc(upperSymbol, batchStart, batchEnd);

      // 3. DB 결과를 DTO 변환 후 개별 캐시에 저장 (수동 캐싱)
      candles.forEach(c -> {
        BigDecimal ratio = BigDecimal.ONE;
        if (c.getClose() != null && c.getClose().compareTo(BigDecimal.ZERO) > 0
          && c.getAdjustedClose() != null) {
          ratio = c.getAdjustedClose().divide(c.getClose(), 8, java.math.RoundingMode.HALF_UP);
        }

        OHLCPriceDto dto = OHLCPriceDto.builder()
          .symbol(c.getSymbol())
          .date(c.getDate())
          .openPrice(c.getOpen() != null ? c.getOpen().multiply(ratio) : null)
          .highPrice(c.getHigh() != null ? c.getHigh().multiply(ratio) : null)
          .lowPrice(c.getLow() != null ? c.getLow().multiply(ratio) : null)
          .closePrice(c.getClose() != null ? c.getClose().multiply(ratio) : null)
          .adjustedClose(c.getAdjustedClose())
          .volume(c.getVolume())
          .currency(c.getCurrency())
          .available(true)
          .build();

        // 개별 캐시에 수동 저장
        org.springframework.cache.Cache ohlcCache = cacheManager.getCache("ohlc");
        if (ohlcCache != null) {
          ohlcCache.put(upperSymbol + ":" + c.getDate(), dto);
        }

        result.add(dto);
      });
    }

    // 4. 날짜순 정렬
    result.sort(Comparator.comparing(OHLCPriceDto::getDate));

    log.info("OHLC 범위 조회 완료: symbol={}, total={}, cacheHit={}, cacheMiss={}",
      symbol, result.size(), result.size() - cacheMissDates.size(), cacheMissDates.size());

    return result;
  }

  @Override
  @Cacheable(cacheNames = "currentPrice", key = "#symbol.toUpperCase()")
  public StockPriceDto getCurrentPrice(String symbol) {
    log.debug("현재가 조회 요청: symbol={}", symbol);

    String upperSymbol = symbol.toUpperCase();
    Optional<Candle> latestCandle = candleRepository.findLatestBySymbol(upperSymbol);

    if (latestCandle.isEmpty()) {
      log.warn("현재가 데이터 없음: symbol={}", symbol);
      return StockPriceDto.builder()
        .symbol(upperSymbol)
        .available(false)
        .build();
    }

    Candle c = latestCandle.get();

    // adjustedClose 사용 (주식 분할/배당 반영)
    BigDecimal currentPrice = c.getAdjustedClose() != null ? c.getAdjustedClose() : c.getClose();

    // 전일 데이터 조회 및 변화율 계산 (QueryDSL 사용)
    Optional<Candle> previousCandle = candleRepository
      .findLatestBySymbolBeforeDate(upperSymbol, c.getDate());

    BigDecimal previousClose = previousCandle
      .map(p -> p.getAdjustedClose() != null ? p.getAdjustedClose() : p.getClose())
      .orElse(currentPrice);
    BigDecimal changePercent = calculateChangePercent(currentPrice, previousClose);

    // Asset에서 name과 marketCap 조회
    var asset = assetRepository.findById(upperSymbol).orElse(null);
    String name = asset != null ? asset.getName() : null;
    Long marketCap = asset != null ? asset.getMarketCap() : null;

    // 주가 정보 DTO 생성 (adjustedClose 사용)
    StockPriceDto result = StockPriceDto.builder()
      .symbol(c.getSymbol())
      .name(name)
      .date(c.getDate())
      .currentPrice(currentPrice)
      .previousClose(previousClose)
      .changePercent(changePercent)
      .volume(c.getVolume())
      .currency(c.getCurrency())
      .marketCap(marketCap)
      .available(true)
      .build();

    log.debug("현재가 조회 성공: symbol={}, price={}, previousClose={}, change={}%",
      symbol, c.getClose(), previousClose, changePercent);
    return result;
  }

  @Override
  public List<OHLCPriceDto> getMultipleOHLCPrices(List<String> symbols, LocalDate startDate,
    LocalDate endDate) {
    log.debug("다중 OHLC 조회 요청: symbols={}, startDate={}, endDate={}", symbols, startDate, endDate);

    // 종목코드 대문자 변환 및 범위 조회
    List<String> upperSymbols = symbols.stream().map(String::toUpperCase).toList();
    List<Candle> candles = candleRepository.findBySymbolsAndDateRange(upperSymbols, startDate,
      endDate);

    // 캔들 데이터 DTO 변환 (adjustedClose 비율로 모든 OHLC 조정)
    List<OHLCPriceDto> result = candles.stream()
      .map(c -> {
        // adjustedClose 비율 계산 (액면분할/배당 반영)
        BigDecimal ratio = BigDecimal.ONE;
        if (c.getClose() != null && c.getClose().compareTo(BigDecimal.ZERO) > 0
          && c.getAdjustedClose() != null) {
          ratio = c.getAdjustedClose().divide(c.getClose(), 8, java.math.RoundingMode.HALF_UP);
        }

        return OHLCPriceDto.builder()
          .symbol(c.getSymbol())
          .date(c.getDate())
          .openPrice(c.getOpen() != null ? c.getOpen().multiply(ratio) : null)
          .highPrice(c.getHigh() != null ? c.getHigh().multiply(ratio) : null)
          .lowPrice(c.getLow() != null ? c.getLow().multiply(ratio) : null)
          .closePrice(c.getClose() != null ? c.getClose().multiply(ratio) : null)
          .adjustedClose(c.getAdjustedClose())
          .volume(c.getVolume())
          .currency(c.getCurrency())
          .available(true)
          .build();
      })
      .toList();

    log.debug("다중 OHLC 조회 성공: count={}", result.size());
    return result;
  }

  @Override
  public List<OHLCPriceDto> getCandlesWithDividends(String symbol, LocalDate startDate,
    LocalDate endDate) {
    log.debug("배당 포함 캔들 조회 요청: symbol={}, startDate={}, endDate={}", symbol, startDate, endDate);

    // 배당이 있는 날짜의 캔들 데이터 조회
    String upperSymbol = symbol.toUpperCase();
    List<Candle> candlesWithDividends = candleRepository.findCandlesWithDividends(upperSymbol,
      startDate, endDate);

    // 캔들 데이터 DTO 변환 (adjustedClose 비율로 모든 OHLC 조정)
    List<OHLCPriceDto> result = candlesWithDividends.stream()
      .map(c -> {
        // adjustedClose 비율 계산 (액면분할/배당 반영)
        BigDecimal ratio = BigDecimal.ONE;
        if (c.getClose() != null && c.getClose().compareTo(BigDecimal.ZERO) > 0
          && c.getAdjustedClose() != null) {
          ratio = c.getAdjustedClose().divide(c.getClose(), 8, java.math.RoundingMode.HALF_UP);
        }

        return OHLCPriceDto.builder()
          .symbol(c.getSymbol())
          .date(c.getDate())
          .openPrice(c.getOpen() != null ? c.getOpen().multiply(ratio) : null)
          .highPrice(c.getHigh() != null ? c.getHigh().multiply(ratio) : null)
          .lowPrice(c.getLow() != null ? c.getLow().multiply(ratio) : null)
          .closePrice(c.getClose() != null ? c.getClose().multiply(ratio) : null)
          .adjustedClose(c.getAdjustedClose())
          .volume(c.getVolume())
          .currency(c.getCurrency())
          .available(true)
          .build();
      })
      .toList();

    log.debug("배당 포함 캔들 조회 성공: count={}", result.size());
    return result;
  }

  @Override
  public Page<StockPriceDto> getAllStocksWithPrices(
    Pageable pageable,
    String direction,
    String assetType) {
    log.debug("전체 종목 목록과 주가 조회 요청: page={}, size={}, direction={}, assetType={}",
      pageable.getPageNumber(), pageable.getPageSize(), direction, assetType);

    // DB 레벨 페이징 + 정렬 + 필터링 (QueryDSL)
    boolean ascending = "asc".equalsIgnoreCase(direction);
    Page<Asset> assetPage = assetRepository.findActiveSortedByMarketCap(pageable, ascending,
      assetType);
    List<Asset> assets = assetPage.getContent();
    List<String> symbols = assets.stream().map(Asset::getSymbol).toList();

    // 해당 페이지 심볼들의 최근 2일 캔들 데이터를 일괄 조회 (최신가 + 전일가)
    List<Candle> recentCandles = candleRepository.findRecentBySymbols(symbols, 2);

    // 심볼별로 캔들 그룹화 (날짜 내림차순 정렬 상태 유지)
    var candlesBySymbol = recentCandles.stream()
      .collect(Collectors.groupingBy(Candle::getSymbol));

    List<StockPriceDto> stockPrices = new ArrayList<>();

    for (Asset asset : assets) {
      try {
        List<Candle> symbolCandles = candlesBySymbol.get(asset.getSymbol());

        if (symbolCandles == null || symbolCandles.isEmpty()) {
          // 데이터가 없는 경우
          StockPriceDto stockPrice = StockPriceDto.builder()
            .symbol(asset.getSymbol())
            .name(asset.getName())
            .currentPrice(null)
            .previousClose(null)
            .changePercent(null)
            .volume(null)
            .marketCap(asset.getMarketCap())
            .assetType(asset.getAssetType())
            .date(LocalDate.now())
            .currency(asset.getCurrency())
            .available(false)
            .build();
          stockPrices.add(stockPrice);
          continue;
        }

        // 이미 날짜 내림차순 정렬되어 있으므로 첫 번째가 최신, 두 번째가 전일
        Candle latest = symbolCandles.getFirst();

        // adjustedClose 사용 (주식 분할/배당 반영)
        BigDecimal currentPrice = latest.getAdjustedClose() != null
          ? latest.getAdjustedClose()
          : latest.getClose();

        BigDecimal previousClose = symbolCandles.size() > 1
          ? (symbolCandles.get(1).getAdjustedClose() != null
          ? symbolCandles.get(1).getAdjustedClose()
          : symbolCandles.get(1).getClose())
          : currentPrice;

        BigDecimal changePercent = calculateChangePercent(currentPrice, previousClose);

        StockPriceDto stockPrice = StockPriceDto.builder()
          .symbol(asset.getSymbol())
          .name(asset.getName())
          .currentPrice(currentPrice)        // 조정 종가
          .previousClose(previousClose)      // 전일 조정 종가
          .changePercent(changePercent)      // (조정 종가 - 전일 조정 종가) / 전일 조정 종가
          .volume(latest.getVolume())
          .marketCap(asset.getMarketCap())
          .assetType(asset.getAssetType())
          .date(latest.getDate())            // 최신 데이터의 날짜
          .currency(asset.getCurrency())
          .available(true)
          .build();

        stockPrices.add(stockPrice);
      } catch (Exception e) {
        log.warn("종목 가격 정보 조회 실패: symbol={}", asset.getSymbol(), e);
      }
    }

    log.debug("전체 종목 목록과 주가 조회 성공: {} 개 (전체 {}개 중)",
      stockPrices.size(), assetPage.getTotalElements());

    return new org.springframework.data.domain.PageImpl<>(
      stockPrices, pageable, assetPage.getTotalElements());
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
