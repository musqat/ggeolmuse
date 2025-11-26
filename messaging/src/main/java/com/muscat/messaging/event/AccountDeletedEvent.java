package com.muscat.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 계좌 삭제 이벤트
 * trade-service: 해당 계좌의 모든 거래 내역 삭제 Holdings, TradeHistory 등 관련 데이터 정리
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AccountDeletedEvent extends BaseEvent {

  //삭제된 계좌의 사용자 ID
  private String userId;

  //삭제된 계좌 ID
  private Long accountId;

  //삭제된 계좌 번호
  private String accountNumber;

  //계좌 이름
  private String accountName;

  //삭제 사유
  private String deletionReason;
}
