package com.muscat.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.util.Map;

/**
 * 이메일 발송 이벤트
 *
 * user-service에서 이메일 발송 요청시 발행되는 이벤트
 * 비동기 이메일 발송으로 사용자 응답 속도 개선
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class EmailSendEvent extends BaseEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 수신자 이메일 주소
     */
    private String to;

    /**
     * 발신자 이메일 주소 (선택)
     */
    private String from;

    /**
     * 이메일 제목
     */
    private String subject;

    /**
     * 이메일 내용 (HTML 또는 Plain Text)
     */
    private String content;

    /**
     * 이메일 타입 (VERIFICATION, PASSWORD_RESET 등)
     */
    private String emailType;

    /**
     * 템플릿 변수 (선택)
     */
    private Map<String, String> templateVariables;

    /**
     * 사용자 ID (추적용, 선택)
     */
    private String userId;
}
