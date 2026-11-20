package com.muscat.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

// 탈퇴 · 강제 탈퇴한 사용자 이벤트
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserDeletedEvent extends BaseEvent {

  // 탈퇴한 사용자의 Keycloak ID(토큰 sub)
  private String keycloakId;
}
