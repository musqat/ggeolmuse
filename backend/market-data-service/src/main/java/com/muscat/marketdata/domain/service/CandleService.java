package com.muscat.marketdata.domain.service;

import com.muscat.marketdata.common.enums.StockSort;
import com.muscat.marketdata.domain.dto.OHLCPriceDto;
import com.muscat.marketdata.domain.dto.StockPriceDto;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

// 캔들(OHLC) · 현재가 조회
public interface CandleService {

  OHLCPriceDto getOHLCPrice(String symbol, LocalDate date);

  // 일자별 캔들 · 배당수익률 정렬 순서 캐시를 모두 비움
  void evictCandleCaches();

  List<OHLCPriceDto> getOHLCPriceRange(String symbol, LocalDate startDate, LocalDate endDate);

  // 최신 캔들과 전일 대비 변화율
  StockPriceDto getCurrentPrice(String symbol);

  List<OHLCPriceDto> getMultipleOHLCPrices(List<String> symbols, LocalDate startDate,
    LocalDate endDate);

  // 배당이 있는 날의 캔들만
  List<OHLCPriceDto> getCandlesWithDividends(String symbol, LocalDate startDate, LocalDate endDate);

  // 시가총액 · 배당수익률 순 페이지. assetType 은 null · ALL · EQUITY · ETF
  Page<StockPriceDto> getAllStocksWithPrices(
    Pageable pageable,
    String direction,
    String assetType,
    StockSort sort);
}
