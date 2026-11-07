package com.muscat.user.domain.account.service;

import com.muscat.user.common.enums.response.AccountResponse;
import com.muscat.user.common.exception.AccountException;
import com.muscat.user.domain.account.dto.request.IdempotencyKey;
import com.muscat.user.domain.account.entity.IdempotencyRecord;
import com.muscat.user.domain.account.repository.IdempotencyRecordRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// 입금 · 환전의 중복 요청 키 기록(하루 안에 같은 사용자 · 같은 키는 처음 요청 하나만 실행)
@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyGuard {

  private final IdempotencyRecordRepository repository;

  // 같은 키 · 같은 요청이 이미 끝났으면 true, 같은 키에 다른 요청이면 422
  public boolean isDone(Long userId, IdempotencyKey key, String fingerprint) {
    return repository.findByUserIdAndIdempotencyKey(userId, key.value())
      .map(record -> {
        if (!record.getFingerprint().equals(fingerprint)) {
          throw new AccountException(AccountResponse.IDEMPOTENCY_KEY_REUSED);
        }
        log.info("중복 요청 키로 다시 온 요청: 사용자={}, 키={}", userId, key.value());
        return true;
      })
      .orElse(false);
  }

  // 트랜잭션 안에서 키 행 넣기(같은 키가 이미 있거나 다른 트랜잭션이 넣는 중이면 그 커밋 뒤 키 충돌)
  public void claim(Long userId, IdempotencyKey key, String fingerprint) {
    repository.saveAndFlush(IdempotencyRecord.of(userId, key.value(), fingerprint, LocalDateTime.now()));
  }
}
