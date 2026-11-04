package com.muscat.trade.domain.repository;

import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.domain.dto.TradePosition;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface TradeRepositoryCustom {

    // 그 상태의 체결이 있는 사용자 · 계좌 · 종목 자리
    List<TradePosition> findDistinctPositions(TradeStatus status);

    // 그 날짜 전에 이 종목을 체결한 사용자 · 계좌 쌍(계좌 순)
    List<TradePosition> findPositionsTradedBefore(String symbol, TradeStatus status, LocalDate before);

    // FIFO 방식으로 매도 가능 수량 계산
    BigDecimal calculateSellableQuantity(
            String userId,
            Long accountId,
            String symbol,
            LocalDate sellDate
    );
}
