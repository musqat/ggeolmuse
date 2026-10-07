package com.muscat.user.domain.account.entity;

import com.muscat.user.common.enums.type.SettlementStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

// 체결 이벤트 처리 결과. tradeId 하나에 한 행
@Entity
@Table(name = "trade_settlements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TradeSettlement implements Persistable<Long> {

  @Id
  private Long tradeId; // trade-service 체결 ID

  private Long accountId; // 이벤트에 실린 계좌 ID. 계좌 ID 를 싣기 전 이벤트는 비어 있음

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private SettlementStatus status;

  @Column(length = 50)
  private String reasonCode; // 반영하지 못한 사유 (AccountResponse 이름)

  @Column(nullable = false)
  private LocalDateTime processedAt;

  // save 가 merge 대신 persist 로 INSERT 하게 한다. 같은 tradeId 가 동시에 오면 기본 키 충돌로 막힌다
  @Transient
  private boolean newEntity = true;

  public static TradeSettlement applied(Long tradeId, Long accountId, LocalDateTime processedAt) {
    TradeSettlement settlement = new TradeSettlement();
    settlement.tradeId = tradeId;
    settlement.accountId = accountId;
    settlement.status = SettlementStatus.APPLIED;
    settlement.processedAt = processedAt;
    return settlement;
  }

  public static TradeSettlement rejected(Long tradeId, Long accountId, String reasonCode,
    LocalDateTime processedAt) {
    TradeSettlement settlement = new TradeSettlement();
    settlement.tradeId = tradeId;
    settlement.accountId = accountId;
    settlement.status = SettlementStatus.REJECTED;
    settlement.reasonCode = reasonCode;
    settlement.processedAt = processedAt;
    return settlement;
  }

  @Override
  public Long getId() {
    return tradeId;
  }

  @Override
  public boolean isNew() {
    return newEntity;
  }

  @PostLoad
  @PostPersist
  void markPersisted() {
    this.newEntity = false;
  }
}
