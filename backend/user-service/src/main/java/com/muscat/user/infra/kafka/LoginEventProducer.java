package com.muscat.user.infra.kafka;

import com.muscat.messaging.event.UserLoginFailedEvent;
import com.muscat.messaging.event.UserLoginSuccessEvent;
import com.muscat.messaging.kafka.EventPublishing;
import com.muscat.user.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

// 로그인 성공 · 실패 이벤트 발행
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginEventProducer {

    private static final String LOGIN_SUCCESS_TOPIC = "user.login.success";
    private static final String LOGIN_FAILED_TOPIC = "user.login.failed";

    private final KafkaTemplate<String, UserLoginSuccessEvent> loginSuccessKafkaTemplate;
    private final KafkaTemplate<String, UserLoginFailedEvent> loginFailedKafkaTemplate;

    // loginMethod: PASSWORD · GOOGLE_OAUTH
    public void publishLoginSuccess(User user, String loginMethod, String ipAddress,
                                      String userAgent, Boolean tokenIssued) {
        UserLoginSuccessEvent event = UserLoginSuccessEvent.builder()
                .userId(String.valueOf(user.getId()))
                .email(user.getEmail())
                .nickname(user.getNickname())
                .loginMethod(loginMethod)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .tokenIssued(tokenIssued)
                .build();
        EventPublishing.stamp(event, "USER_LOGIN_SUCCESS", "user-service");

        log.info("로그인 성공 이벤트 발행 중: userId={}, email={}, loginMethod={}",
                user.getId(), user.getEmail(), loginMethod);

        EventPublishing.send(loginSuccessKafkaTemplate, LOGIN_SUCCESS_TOPIC, user.getEmail(), event);
    }

    public void publishLoginFailed(String email, String failureReason, String failureMessage,
                                     String loginMethod, String ipAddress, String userAgent) {
        UserLoginFailedEvent event = UserLoginFailedEvent.builder()
                .email(email)
                .failureReason(failureReason)
                .failureMessage(failureMessage)
                .loginMethod(loginMethod)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();
        EventPublishing.stamp(event, "USER_LOGIN_FAILED", "user-service");

        log.info("로그인 실패 이벤트 발행 중: email={}, failureReason={}, loginMethod={}",
                email, failureReason, loginMethod);

        EventPublishing.send(loginFailedKafkaTemplate, LOGIN_FAILED_TOPIC, email, event);
    }
}
