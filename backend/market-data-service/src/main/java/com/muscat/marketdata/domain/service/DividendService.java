package com.muscat.marketdata.domain.service;

import com.muscat.marketdata.domain.dto.DividendDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Dividend(배당) 데이터 조회 서비스
 *
 * 배당 이력 조회 및 고배당주 검색을 담당합니다.
 */
public interface DividendService {

    /**
     * 종목의 배당 이력 조회
     *
     * @param symbol 종목 심볼
     * @param startDate 시작 날짜
     * @param endDate 종료 날짜
     * @return 배당 이력 리스트
     */
    List<DividendDto> getDividendHistory(String symbol, LocalDate startDate, LocalDate endDate);

    /**
     * 고배당 주식 검색 (최소 금액 이상)
     *
     * @param minAmount 최소 배당 금액
     * @param fromDate 기준 날짜
     * @return 고배당 주식 리스트
     */
    List<DividendDto> findHighDividendStocks(BigDecimal minAmount, LocalDate fromDate);
}
