package com.muscat.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 사용자 로그인 실패 이벤트
 *
 * 사용자 로그인 시도가 실패했을 때 발행됩니다.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserLoginFailedEvent extends BaseEvent {

    /**
     * 로그인 시도한 이메일
     */
    private String email;

    /**
     * 실패 사유
     * "INVALID_CREDENTIALS" - 잘못된 이메일 또는 비밀번호
     * "EMAIL_NOT_VERIFIED" - 이메일 인증 미완료
     * "ACCOUNT_NOT_FOUND" - 계정이 존재하지 않음
     * "ACCOUNT_LOCKED" - 계정 잠금
     * "KEYCLOAK_ERROR" - Keycloak 연동 오류
     */
    private String failureReason;

    /**
     * 실패 상세 메시지
     */
    private String failureMessage;

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
}
