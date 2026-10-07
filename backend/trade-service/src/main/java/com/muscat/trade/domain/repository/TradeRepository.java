package com.muscat.trade.domain.repository;

import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.domain.entity.Trade;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// Trade Repository
@Repository
public interface TradeRepository extends JpaRepository<Trade, Long>, TradeRepositoryCustom {

  // 사용자별 거래 내역 조회 (최신순, 페이징)
  Page<Trade> findByUserIdOrderByExecutedAtDesc(String userId, Pageable pageable);

  // 사용자/종목별 거래 내역 조회 (최신순)
  List<Trade> findByUserIdAndSymbolOrderByExecutedAtDesc(String userId, String symbol);

  // 배당 계산용. 상태로 거른 사용자/종목별 체결 (오래된순)
  List<Trade> findByUserIdAndSymbolAndStatusOrderByTradeDateAsc(String userId, String symbol,
    TradeStatus status);

  // 보유 다시 쌓기용. 한 종목 체결을 실제 반영 순서로
  List<Trade> findByUserIdAndAccountIdAndSymbolAndStatusOrderByExecutedAtAscIdAsc(String userId,
    Long accountId, String symbol, TradeStatus status);

  // 같은 체결을 동시에 취소하지 않게 잠가서 읽는다
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from Trade t where t.id = :id")
  Optional<Trade> findByIdForUpdate(@Param("id") Long id);

  // 계좌 삭제 시 모든 거래 내역 삭제
  void deleteByAccountId(Long accountId);

  // 기준 시각 전에 만들고 발행 시각이 빈 체결 100건 (id 순)
  List<Trade> findTop100ByEventPublishedAtIsNullAndCreatedAtBeforeOrderByIdAsc(LocalDateTime before);

  // 못 보낸 체결 수 (trade.events.unpublished)
  long countByEventPublishedAtIsNullAndCreatedAtBefore(LocalDateTime before);

  // 체결 이벤트 발행 시각 저장
  @Modifying
  @Query("update Trade t set t.eventPublishedAt = :publishedAt where t.id = :id")
  int markEventPublished(@Param("id") Long id, @Param("publishedAt") LocalDateTime publishedAt);
}
