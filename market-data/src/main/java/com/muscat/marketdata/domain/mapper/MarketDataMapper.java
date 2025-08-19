package com.muscat.marketdata.domain.mapper;

import com.muscat.marketdata.domain.dto.CandleDto;
import com.muscat.marketdata.domain.dto.DividendDto;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.entity.Candle;
import com.muscat.marketdata.domain.entity.CandleId;
import com.muscat.marketdata.domain.entity.Dividend;
import com.muscat.marketdata.domain.entity.DividendId;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 외부 API DTO를 도메인 엔티티로 변환
 * NASDAQ 100 백테스트 프로젝트 전용 (신뢰할 수 있는 데이터 소스)
 */
public final class MarketDataMapper {

  private MarketDataMapper() {}

  // ===== Asset 생성 (미국 주식 전용) =====

  /**
   * 미국 주식 Asset 생성
   */
  public static Asset toAsset(String symbol, String name) {
    return Asset.builder()
        .symbol(symbol.trim().toUpperCase())
        .name(name.trim())
        .country("US")
        .currency("USD")
        .assetType("EQUITY")
        .build();
  }

  /**
   * 심볼만으로 Asset 생성 (종목명 = 심볼)
   */
  public static Asset toAsset(String symbol) {
    return toAsset(symbol, symbol.toUpperCase());
  }

  // ===== Candle 변환 =====

  /**
   * DTO를 Candle 엔티티로 변환
   */
  public static Candle toCandle(CandleDto dto, String fallbackSymbol) {
    if (dto == null || dto.getDate() == null) return null;
    if (!hasValidOhlcv(dto)) return null;

    String symbol = determineSymbol(dto.getSymbol(), fallbackSymbol);
    if (symbol == null) return null;

    BigDecimal adjustedClose = dto.getAdjustedClose() != null
        ? dto.getAdjustedClose()
        : dto.getClose();

    return Candle.builder()
        .id(new CandleId(symbol, dto.getDate()))
        .open(dto.getOpen())
        .high(dto.getHigh())
        .low(dto.getLow())
        .close(dto.getClose())
        .adjustedClose(adjustedClose)
        .volume(dto.getVolume())
        .dividendAmount(BigDecimal.ZERO)
        .splitCoefficient(BigDecimal.ONE)
        .build();
  }

  /**
   * DTO 리스트를 Candle 리스트로 변환
   */
  public static List<Candle> toCandles(List<CandleDto> dtos, String fallbackSymbol) {
    if (dtos == null || dtos.isEmpty()) return new ArrayList<>();

    List<Candle> results = new ArrayList<>();
    for (CandleDto dto : dtos) {
      Candle candle = toCandle(dto, fallbackSymbol);
      if (candle != null) {
        results.add(candle);
      }
    }
    return results;
  }

  // ===== Dividend 변환 =====

  /**
   * DividendDto를 Dividend 엔티티로 변환
   */
  public static Dividend toDividend(DividendDto dto) {
    if (dto == null || dto.getSymbol() == null || dto.getExDate() == null || dto.getAmount() == null) {
      return null;
    }

    return Dividend.builder()
        .id(new DividendId(dto.getSymbol(), dto.getExDate()))
        .amount(dto.getAmount())
        .currency(dto.getCurrency() != null ? dto.getCurrency() : "USD")
        .build();

  }

  /**
   * DividendDto 리스트를 Dividend 리스트로 변환
   */
  public static List<Dividend> toDividends(List<DividendDto> dtos) {
    if (dtos == null || dtos.isEmpty()) return new ArrayList<>();

    List<Dividend> results = new ArrayList<>();
    for (DividendDto dto : dtos) {
      Dividend dividend = toDividend(dto);
      if (dividend != null) {
        results.add(dividend);
      }
    }
    return results;
  }

  // ===== 내부 유틸리티 메서드 =====

  private static boolean hasValidOhlcv(CandleDto dto) {
    return dto.getOpen() != null
        && dto.getHigh() != null
        && dto.getLow() != null
        && dto.getClose() != null
        && dto.getVolume() != null;
  }

  private static String determineSymbol(String dtoSymbol, String fallbackSymbol) {
    if (dtoSymbol != null && !dtoSymbol.trim().isEmpty()) {
      return dtoSymbol.trim().toUpperCase();
    }
    if (fallbackSymbol != null && !fallbackSymbol.trim().isEmpty()) {
      return fallbackSymbol.trim().toUpperCase();
    }
    return null;
  }
}