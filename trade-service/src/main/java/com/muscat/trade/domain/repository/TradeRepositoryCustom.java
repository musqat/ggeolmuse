package com.muscat.trade.domain.repository;

import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.domain.entity.Trade;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface TradeRepositoryCustom {

    // 사용자가 거래한 고유 심볼 목록 조회
    List<String> findDistinctSymbolsByUserId(String userId);

    // 복잡한 거래 내역 검색 (다중 조건 + 페이지네이션)
    Page<Trade> findTradesWithComplexFilters(
            String userId,
            Long accountId,
            String symbol,
            TradeType tradeType,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            Pageable pageable
    );

    // FIFO 방식으로 매도 가능 수량 계산
    BigDecimal calculateSellableQuantity(
            String userId,
            Long accountId,
            String symbol,
            LocalDate sellDate
    );
}
