package com.muscat.trade.domain.repository;

import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.domain.dto.TradePosition;
import com.muscat.trade.domain.entity.Trade;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface TradeRepositoryCustom {

  // 그 상태의 체결이 있는 사용자 · 계좌 · 종목 자리
  List<TradePosition> findDistinctPositions(TradeStatus status);

  // 종목마다 모든 상태 체결 가운데 가장 이른 거래일
  Map<String, LocalDate> findFirstTradeDateBySymbol();

  // 받은 날짜 전에 받은 종목을 체결한 사용자 · 계좌 쌍(계좌 순)
  List<TradePosition> findPositionsTradedBefore(String symbol, TradeStatus status, LocalDate before);

  // 같은 체결의 동시 취소를 막는 잠금 읽기
  Optional<Trade> findByIdForUpdate(Long id);

  // 체결 이벤트 발행 시각 저장(바꾼 행 수)
  long markEventPublished(Long id, LocalDateTime publishedAt);

  // 매도일에 팔 수 있는 수량(뒤 날짜 누적 보유도 음수가 안 되는 만큼)
  BigDecimal calculateSellableQuantity(
    String userId,
    Long accountId,
    String symbol,
    LocalDate sellDate
  );
}
