package com.muscat.backtest.domain.port;

import com.muscat.backtest.domain.dto.market.DividendDto;
import com.muscat.backtest.domain.dto.market.FxRateDto;
import com.muscat.commonlib.dto.OHLCPriceDto;
import com.muscat.commonlib.dto.StockPriceDto;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

// 백테스트 시세 · 환율 · 배당 조회 포트
public interface MarketDataPort {

  // 환율을 못 받은 날에 쓰는 기본 환율(원/USD)
  BigDecimal defaultFxRate();

  // 받은 날짜 일봉(없으면 null)
  OHLCPriceDto getOHLCPrice(String symbol, String date);

  List<OHLCPriceDto> getOHLCPriceRange(String symbol, String startDate, String endDate);

  StockPriceDto getCurrentPrice(String symbol);

  // 기간 배당 이력(못 받으면 null)
  List<DividendDto> getDividendHistory(String symbol, String startDate, String endDate);

  // 받은 날짜 환율(없으면 null)
  FxRateDto getFxRate(String date);

  // 날짜마다 환율(환율이 있는 날짜만)
  Map<String, BigDecimal> getBulkFxRates(List<String> dates);

  FxRateDto getLatestFxRate();
}
