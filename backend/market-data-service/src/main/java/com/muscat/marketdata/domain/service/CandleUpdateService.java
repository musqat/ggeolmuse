package com.muscat.marketdata.domain.service;

import java.time.LocalDate;

// 캔들 · 배당 저장(각각 저장한 개수 반환)
public interface CandleUpdateService {

  int saveCandles(String symbol, LocalDate from, LocalDate to);

  int saveDividends(String symbol, LocalDate from, LocalDate to);

  int saveBoth(String symbol, LocalDate from, LocalDate to);
}
