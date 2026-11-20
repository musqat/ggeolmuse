package com.muscat.trade.domain.repository;

import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.domain.entity.Trade;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
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

  // 한 종목 체결(체결 시각 순)
  List<Trade> findByUserIdAndAccountIdAndSymbolAndStatusOrderByExecutedAtAscIdAsc(String userId,
                                                                                  Long accountId, String symbol, TradeStatus status);

  List<Trade> findByUserIdAndStatusOrderByExecutedAtAscIdAsc(String userId, TradeStatus status);

  // 기준 거래일 뒤 한 종목 체결이 있는지
  boolean existsByUserIdAndAccountIdAndSymbolAndStatusAndTradeDateAfter(String userId, Long accountId,
                                                                        String symbol, TradeStatus status, LocalDate tradeDate);

  // 모든 상태(대기 · 완료 · 취소)의 체결
  List<Trade> findByUserId(String userId);

  List<Trade> findByUserIdAndAccountId(String userId, Long accountId);

  // 계좌 주인 찾기용(취소된 체결 포함)
  Optional<Trade> findFirstByAccountIdOrderByIdAsc(Long accountId);

  // 받은 날짜 전 한 종목의 가장 이른 체결(모든 상태)
  Optional<Trade> findFirstBySymbolAndTradeDateBeforeOrderByTradeDateAsc(String symbol, LocalDate before);

  // 분할 반영용 잠금 읽기(받은 날짜 전 한 종목의 모든 상태 체결, ID 순)
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  List<Trade> findBySymbolAndTradeDateBeforeOrderByIdAsc(String symbol, LocalDate before);

  // 계좌 삭제 시 모든 거래 내역 삭제
  void deleteByAccountId(Long accountId);

  // 탈퇴한 사용자의 행 전부 삭제(Keycloak ID)
  void deleteByUserId(String userId);

  // 기준 시각 전에 만들고 발행 시각이 빈 체결 100건 (id 순)
  List<Trade> findTop100ByEventPublishedAtIsNullAndCreatedAtBeforeOrderByIdAsc(LocalDateTime before);

  // 못 보낸 체결 수 (trade.events.unpublished)
  long countByEventPublishedAtIsNullAndCreatedAtBefore(LocalDateTime before);

  // 기준 시각 전에 발행하고도 아직 그 상태인 체결 100건 (id 순)
  List<Trade> findTop100ByTradeTypeAndStatusAndEventPublishedAtBeforeOrderByIdAsc(TradeType tradeType,
                                                                                  TradeStatus status, LocalDateTime before);

  // 기준 시각 전에 만들고 아직 그 상태인 체결 수 (trade.buys.pending)
  long countByTradeTypeAndStatusAndCreatedAtBefore(TradeType tradeType, TradeStatus status, LocalDateTime cutoff);
}
