package com.muscat.trade.domain.service;

import com.muscat.trade.domain.entity.DividendHistory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface DividendService {

  // 일일 배당 처리 스케줄러 (매일 실행)
  void processDailyDividends();

  // 특정 종목의 배당 처리
  void processDividendForSymbol(String symbol, LocalDate dividendDate, BigDecimal dividendPerShare);

  // 배당 요약 정보 조회
  Map<String, BigDecimal> getDividendSummary(String userId, int year);

  // 배당 내역 조회
  List<DividendHistory> getDividendHistory(String userId);

  // 연도별 배당 내역 조회
  List<DividendHistory> getDividendHistoryByYear(String userId, int year);

  // 종목별 배당 내역 조회
  List<DividendHistory> getDividendHistoryBySymbol(String userId, String symbol);
}