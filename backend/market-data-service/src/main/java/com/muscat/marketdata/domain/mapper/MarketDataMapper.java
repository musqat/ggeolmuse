package com.muscat.marketdata.domain.mapper;

import com.muscat.marketdata.domain.dto.CandleDto;
import com.muscat.marketdata.domain.dto.DividendDto;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.entity.Candle;
import com.muscat.marketdata.domain.entity.Dividend;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

public final class MarketDataMapper {

  private MarketDataMapper() {
  }

  public static Asset toAsset(String symbol, String name) {
    return Asset.builder()
      .symbol(symbol.trim().toUpperCase())
      .name(name.trim())
      .country("US")
      .currency("USD")
      .assetType("EQUITY")
      .build();
  }

  public static Asset toAsset(String symbol) {
    return toAsset(symbol, symbol.toUpperCase());
  }

  public static Candle toCandle(CandleDto dto, String fallbackSymbol) {
    if (dto == null || dto.getDate() == null || !hasValidOhlcv(dto)) {
      return null;
    }

    String symbol = determineSymbol(dto.getSymbol(), fallbackSymbol);
    if (symbol == null) {
      return null;
    }

    return Candle.builder()
      .symbol(symbol)
      .date(dto.getDate())
      .currency(dto.getCurrency() != null ? dto.getCurrency() : "USD")
      .open(dto.getOpen())
      .high(dto.getHigh())
      .low(dto.getLow())
      .close(dto.getClose())
      .adjustedClose(dto.getAdjustedClose() != null ? dto.getAdjustedClose() : dto.getClose())
      .volume(dto.getVolume())
      .dividendAmount(BigDecimal.ZERO)
      .splitCoefficient(BigDecimal.ONE)
      .build();
  }

  public static List<Candle> toCandles(List<CandleDto> dtos, String fallbackSymbol) {
    if (dtos == null) {
      return List.of();
    }

    return dtos.stream()
      .map(dto -> toCandle(dto, fallbackSymbol))
      .filter(candle -> candle != null)
      .collect(Collectors.toList());
  }

  public static Dividend toDividend(DividendDto dto) {
    if (dto == null || dto.getSymbol() == null || dto.getExDate() == null
      || dto.getAmount() == null) {
      return null;
    }

    return Dividend.builder()
      .symbol(dto.getSymbol())
      .exDate(dto.getExDate())
      .amount(dto.getAmount())
      .currency(dto.getCurrency() != null ? dto.getCurrency() : "USD")
      .build();
  }

  public static List<Dividend> toDividends(List<DividendDto> dtos) {
    if (dtos == null) {
      return List.of();
    }

    return dtos.stream()
      .map(MarketDataMapper::toDividend)
      .filter(dividend -> dividend != null)
      .collect(Collectors.toList());
  }

  private static boolean hasValidOhlcv(CandleDto dto) {
    return dto.getOpen() != null && dto.getHigh() != null
      && dto.getLow() != null && dto.getClose() != null
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
