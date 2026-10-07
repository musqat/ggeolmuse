package com.muscat.trade.domain.repository;

import com.muscat.trade.domain.entity.Trade;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

// Trade Repository
@Repository
public interface TradeRepository extends JpaRepository<Trade, Long>, TradeRepositoryCustom {

  // 사용자별 거래 내역 조회 (최신순, 페이징)
  Page<Trade> findByUserIdOrderByExecutedAtDesc(String userId, Pageable pageable);

  // 사용자/종목별 거래 내역 조회 (최신순)
  List<Trade> findByUserIdAndSymbolOrderByExecutedAtDesc(String userId, String symbol);

  // 사용자/종목별 거래 내역 조회 (오래된순, 평단가 계산용)
  List<Trade> findByUserIdAndSymbolOrderByTradeDateAsc(String userId, String symbol);

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
