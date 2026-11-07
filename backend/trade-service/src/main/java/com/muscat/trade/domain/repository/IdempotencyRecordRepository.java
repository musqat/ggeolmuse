package com.muscat.trade.domain.repository;

import com.muscat.trade.domain.entity.IdempotencyRecord;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

  Optional<IdempotencyRecord> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey);
}
