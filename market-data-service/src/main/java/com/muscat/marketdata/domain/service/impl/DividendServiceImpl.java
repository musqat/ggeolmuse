package com.muscat.marketdata.domain.service.impl;

import com.muscat.marketdata.domain.dto.DividendDto;
import com.muscat.marketdata.domain.entity.Dividend;
import com.muscat.marketdata.domain.repository.DividendRepository;
import com.muscat.marketdata.domain.service.DividendService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dividend(배당) 데이터 조회 서비스 구현체
 *
 * 배당 이력 조회 및 고배당주 검색을 담당합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DividendServiceImpl implements DividendService {

  private final DividendRepository dividendRepository;

  @Override
  public List<DividendDto> getDividendHistory(String symbol, LocalDate startDate,
      LocalDate endDate) {
    log.debug("배당 이력 조회 요청: symbol={}, startDate={}, endDate={}", symbol, startDate, endDate);

    // 지정된 기간의 배당 데이터 조회
    String upperSymbol = symbol.toUpperCase();
    List<Dividend> dividends = dividendRepository.findBySymbolsAndDateRange(
        List.of(upperSymbol), startDate, endDate);

    // 엔티티를 DTO로 변환
    List<DividendDto> result = dividends.stream()
        .map(dividend -> DividendDto.builder()
            .symbol(dividend.getSymbol())
            .exDate(dividend.getExDate())
            .amount(dividend.getAmount())
            .currency(dividend.getCurrency())
            .source("MarketData")
            .build())
        .collect(Collectors.toList());

    log.debug("배당 이력 조회 성공: symbol={}, count={}", symbol, result.size());
    return result;
  }

  @Override
  public List<DividendDto> findHighDividendStocks(BigDecimal minAmount, LocalDate fromDate) {
    log.debug("고배당주 검색 요청: minAmount={}, fromDate={}", minAmount, fromDate);

    // 최소 금액 이상의 배당 데이터 검색
    List<Dividend> highDividends = dividendRepository.findHighDividendStocks(minAmount,
        fromDate);

    // 배당 데이터 DTO 변환
    List<DividendDto> result = highDividends.stream()
        .map(dividend -> DividendDto.builder()
            .symbol(dividend.getSymbol())
            .exDate(dividend.getExDate())
            .amount(dividend.getAmount())
            .currency(dividend.getCurrency())
            .source("MarketData")
            .build())
        .toList();

    log.debug("고배당주 검색 성공: count={}", result.size());
    return result;
  }
}
