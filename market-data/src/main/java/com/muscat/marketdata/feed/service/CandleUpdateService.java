package com.muscat.marketdata.feed.service;

import com.muscat.marketdata.domain.dto.DividendDto;
import com.muscat.marketdata.domain.entity.Candle;
import com.muscat.marketdata.domain.mapper.MarketDataMapper;
import com.muscat.marketdata.domain.repository.CandleRepository;
import com.muscat.marketdata.domain.repository.DividendRepository;
import com.muscat.marketdata.provider.MarketDataProvider.CandleSource;
import com.muscat.marketdata.provider.MarketDataProvider.DividendSource;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 단일 심볼에 대한 캔들/배당 데이터 저장 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CandleUpdateService {

  private final CandleSource candleSource;
  private final DividendSource dividendSource;

  private final CandleRepository candleRepository;
  private final DividendRepository dividendRepository;

  /**
   * 캔들 데이터 저장, 처리된 건수 반환 (대략적)
   */
  @Transactional
  public int saveCandles(String symbol, LocalDate from, LocalDate to) {
    List<Candle> candles = candleSource.fetchDailyAdjusted(symbol, from, to);
    if (candles == null || candles.isEmpty()) {
      return 0;
    }
    candleRepository.saveAll(candles);
    return candles.size();
  }

  /**
   * 배당 정보 저장, 처리된 건수 반환 (대략적)
   */
  @Transactional
  public int saveDividends(String symbol, LocalDate from, LocalDate to) {
    List<DividendDto> dtos = dividendSource.fetchDividends(symbol, from, to);
    if (dtos == null || dtos.isEmpty()) {
      return 0;
    }
    var entities = MarketDataMapper.toDividends(dtos);
    dividendRepository.saveAll(entities);
    return entities.size();
  }

  /**
   * 캔들/배당 데이터 저장, 총 처리 건수 반환
   */
  @Transactional
  public int saveBoth(String symbol, LocalDate from, LocalDate to) {
    int c = saveCandles(symbol, from, to);
    int d = saveDividends(symbol, from, to);
    log.info("[데이터저장] {} 캔들={}, 배당={}", symbol, c, d);
    return c + d;
  }
}