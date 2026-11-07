package com.muscat.trade.domain.service;

import com.muscat.trade.common.enums.response.TradeResponse;
import com.muscat.trade.common.exception.TradeException;
import com.muscat.trade.domain.dto.request.IdempotencyKey;
import com.muscat.trade.domain.entity.IdempotencyRecord;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.repository.IdempotencyRecordRepository;
import com.muscat.trade.domain.repository.TradeRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// 매수 · 매도의 중복 요청 키 기록(같은 사용자 · 같은 키는 처음 주문의 체결 하나)
@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyGuard {

  private final IdempotencyRecordRepository repository;
  private final TradeRepository tradeRepository;

  // 같은 키 · 같은 주문이 이미 끝났으면 그 체결의 지금 상태, 같은 키에 다른 주문이면 422
  public Optional<Trade> findDone(String userId, IdempotencyKey key, String fingerprint) {
    return repository.findByUserIdAndIdempotencyKey(userId, key.value())
      .map(record -> {
        if (!record.getFingerprint().equals(fingerprint)) {
          throw new TradeException(TradeResponse.IDEMPOTENCY_KEY_REUSED);
        }
        log.info("중복 요청 키로 다시 온 주문: 사용자={}, 키={}, 체결={}", userId, key.value(), record.getTradeId());
        return tradeRepository.findById(record.getTradeId())
          .orElseThrow(() -> new TradeException(TradeResponse.TRADE_NOT_FOUND));
      });
  }

  // 트랜잭션 안에서 키 행 넣기(같은 키가 이미 있거나 다른 트랜잭션이 넣는 중이면 그 커밋 뒤 키 충돌)
  public IdempotencyRecord claim(String userId, IdempotencyKey key, String fingerprint) {
    return repository.saveAndFlush(IdempotencyRecord.of(userId, key.value(), fingerprint, LocalDateTime.now()));
  }
}
