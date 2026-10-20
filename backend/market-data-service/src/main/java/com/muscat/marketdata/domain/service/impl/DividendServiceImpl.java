package com.muscat.marketdata.domain.service.impl;

import com.muscat.marketdata.domain.dto.DividendDto;
import com.muscat.marketdata.domain.entity.Dividend;
import com.muscat.marketdata.domain.repository.DividendRepository;
import com.muscat.marketdata.domain.service.DividendService;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
