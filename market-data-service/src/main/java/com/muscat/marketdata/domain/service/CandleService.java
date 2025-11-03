package com.muscat.marketdata.domain.service;

import com.muscat.marketdata.domain.dto.OHLCPriceDto;
import com.muscat.marketdata.domain.dto.StockPriceDto;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Candle(캔들) 데이터 조회 서비스
 * <p>
 * OHLC 가격 데이터 및 주식 현재가 조회를 담당합니다.
 */
public interface CandleService {

  /**
   * 특정 날짜의 OHLC 가격 조회
   *
   * @param symbol 종목 심볼
   * @param date   조회 날짜
   * @return OHLC 가격 데이터
   */
  OHLCPriceDto getOHLCPrice(String symbol, LocalDate date);

  /**
   * 특정 기간의 OHLC 가격 범위 조회
   *
   * @param symbol    종목 심볼
   * @param startDate 시작 날짜
   * @param endDate   종료 날짜
   * @return OHLC 가격 데이터 리스트
   */
  List<OHLCPriceDto> getOHLCPriceRange(String symbol, LocalDate startDate, LocalDate endDate);

  /**
   * 종목의 현재가 조회
   *
   * @param symbol 종목 심볼
   * @return 현재 주가 정보 (최신 캔들 + 전일 대비 변화율)
   */
  StockPriceDto getCurrentPrice(String symbol);

  /**
   * 여러 종목의 OHLC 데이터 일괄 조회
   *
   * @param symbols   종목 심볼 리스트
   * @param startDate 시작 날짜
   * @param endDate   종료 날짜
   * @return OHLC 가격 데이터 리스트
   */
  List<OHLCPriceDto> getMultipleOHLCPrices(List<String> symbols, LocalDate startDate,
    LocalDate endDate);

  /**
   * 배당 지급일의 캔들 데이터 조회
   *
   * @param symbol    종목 심볼
   * @param startDate 시작 날짜
   * @param endDate   종료 날짜
   * @return 배당이 있는 날의 OHLC 가격 데이터
   */
  List<OHLCPriceDto> getCandlesWithDividends(String symbol, LocalDate startDate, LocalDate endDate);

  /**
   * 전체 종목 목록과 현재가 조회 (페이지네이션)
   *
   * @param pageable  페이지 정보 (페이지 번호, 크기)
   * @param direction 정렬 방향 (asc: 오름차순, desc: 내림차순) - 시가총액 기준
   * @return 종목의 주가 정보 페이지
   */
  Page<StockPriceDto> getAllStocksWithPrices(
    Pageable pageable,
    String direction);
}
