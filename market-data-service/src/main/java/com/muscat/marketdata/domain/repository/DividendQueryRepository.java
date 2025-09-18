package com.muscat.marketdata.domain.repository;

import com.muscat.marketdata.domain.entity.Dividend;
import com.muscat.marketdata.domain.entity.QDividend;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

// 배당 데이터 복잡 조회용 Repository
@Repository
@RequiredArgsConstructor
public class DividendQueryRepository {

  private final JPAQueryFactory queryFactory;
  private static final QDividend dividend = QDividend.dividend;

  // 여러 심볼의 배당 이력을 한 번에 조회
  public List<Dividend> findBySymbolsAndDateRange(List<String> symbols, LocalDate startDate,
      LocalDate endDate) {
    return queryFactory
        .selectFrom(dividend)
        .where(dividend.symbol.in(symbols)
            .and(dividend.exDate.between(startDate, endDate)))
        .orderBy(dividend.symbol.asc(), dividend.exDate.desc())
        .fetch();
  }


  // 특정 금액 이상의 배당을 지급하는 종목 검색
  public List<Dividend> findHighDividendStocks(BigDecimal minAmount, LocalDate fromDate) {
    return queryFactory
        .selectFrom(dividend)
        .where(dividend.amount.goe(minAmount)
            .and(dividend.exDate.goe(fromDate)))
        .orderBy(dividend.amount.desc(), dividend.exDate.desc())
        .fetch();
  }


}