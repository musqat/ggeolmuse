package com.muscat.marketdata.domain.service;

import java.time.LocalDate;

/**
 * 캔들 및 배당 데이터 업데이트 서비스 인터페이스
 *
 */
public interface CandleUpdateService {

  /**
   * 캔들 데이터 저장
   *
   * @param symbol 종목 코드
   * @param from   시작 날짜
   * @param to     종료 날짜
   * @return 저장된 캔들 개수
   */
  int saveCandles(String symbol, LocalDate from, LocalDate to);

  /**
   * 배당 데이터 저장
   *
   * @param symbol 종목 코드
   * @param from   시작 날짜
   * @param to     종료 날짜
   * @return 저장된 배당 개수
   */
  int saveDividends(String symbol, LocalDate from, LocalDate to);

  /**
   * 캔들 + 배당 데이터 동시 저장
   *
   * @param symbol 종목 코드
   * @param from   시작 날짜
   * @param to     종료 날짜
   * @return 저장된 전체 데이터 개수 (캔들 + 배당)
   */
  int saveBoth(String symbol, LocalDate from, LocalDate to);
}
