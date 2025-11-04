package com.muscat.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 사용자 로그인 성공 이벤트
 *
 * 사용자가 성공적으로 로그인할 때 발행됩니다.
 * 보안 모니터링, 접속 통계, 사용자 활동 추적 등에 활용할 수 있습니다.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserLoginSuccessEvent extends BaseEvent {

    /**
     * 사용자 ID
     */
    private String userId;

    /**
     * 사용자 이메일
     */
    private String email;

    /**
     * 사용자 닉네임
     */
    private String nickname;

    /**
     * 로그인 방식
     * "PASSWORD" - 이메일/비밀번호 로그인
     * "GOOGLE_OAUTH" - Google OAuth 로그인
     */
    private String loginMethod;

    /**
     * 클라이언트 IP 주소 (있는 경우)
     */
    private String ipAddress;

    /**
     * User Agent 정보 (있는 경우)
     */
    private String userAgent;

    /**
     * Keycloak에서 발급한 토큰 존재 여부
     */
    private Boolean tokenIssued;
}
