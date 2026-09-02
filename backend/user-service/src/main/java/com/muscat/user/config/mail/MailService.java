package com.muscat.user.config.mail;

import com.muscat.user.infra.kafka.EmailEventProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MailService {

  private final EmailEventProducer emailEventProducer;

  public MailService(EmailEventProducer emailEventProducer) {
    this.emailEventProducer = emailEventProducer;
    log.info("MailService 초기화 (Kafka 이벤트 기반)");
  }

  @Value("${app.mail.from:noreply@example.com}")
  private String fromEmail;

  @Value("${app.mail.verification.base-url:http://localhost:8090}")
  private String baseUrl;

  // 이메일 인증 메일 발송 (Kafka 이벤트 발행)
  public void sendVerificationEmail(String toEmail, String token) {
    try {
      String verificationUrl = baseUrl + "/api/auth/verify-email?token=" + token;
      String htmlContent = createEmailTemplate(verificationUrl);

      // Kafka를 통해 이메일 발송 이벤트 발행 (비동기)
      emailEventProducer.publishEmailSend(
        toEmail,
        fromEmail,
        "이메일 인증을 완료해주세요",
        htmlContent,
        "VERIFICATION",
        null,
        null
      );

      log.info("이메일 발송 이벤트 발행 완료 (비동기): to={}, type=VERIFICATION", toEmail);

    } catch (Exception e) {
      log.error("이메일 발송 이벤트 발행 실패: {}", toEmail, e);
      // 이벤트 발행 실패는 사용자 등록 프로세스를 막지 않음
    }
  }

  // 비밀번호 재설정 메일 발송 (Kafka 이벤트 발행)
  public void sendPasswordResetEmail(String toEmail, String token) {
    try {
      String resetUrl = baseUrl + "/reset-password?token=" + token;
      String htmlContent = createPasswordResetTemplate(resetUrl);

      // Kafka를 통해 이메일 발송 이벤트 발행 (비동기)
      emailEventProducer.publishEmailSend(
        toEmail,
        fromEmail,
        "비밀번호 재설정 요청",
        htmlContent,
        "PASSWORD_RESET",
        null,
        null
      );

      log.info("비밀번호 재설정 이메일 발송 이벤트 발행 완료 (비동기): to={}, type=PASSWORD_RESET", toEmail);

    } catch (Exception e) {
      log.error("비밀번호 재설정 이메일 발송 이벤트 발행 실패: {}", toEmail, e);
    }
  }

  //이메일 HTML 템플릿
  private String createEmailTemplate(String verificationUrl) {
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>이메일 인증</title>
        </head>
        <body style="margin: 0; padding: 0; background-color: #f5f5f5; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;">
            <div style="max-width: 500px; margin: 50px auto; background: white; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 10px rgba(0,0,0,0.1);">
                <div style="background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); padding: 40px 30px; text-align: center;">
                    <h1 style="color: white; margin: 0; font-size: 24px; font-weight: 600;">이메일 인증</h1>
                </div>
                <div style="padding: 40px 30px;">
                    <p style="margin: 0 0 24px 0; color: #333; font-size: 16px; line-height: 1.5;">
                        안녕하세요!<br>
                        회원가입을 완료하려면 아래 버튼을 클릭해주세요.
                    </p>
                    <div style="text-align: center; margin: 32px 0;">
                        <a href="%s" style="display: inline-block; background: #667eea; color: white; text-decoration: none; padding: 14px 28px; border-radius: 6px; font-weight: 500; font-size: 16px;">
                            이메일 인증하기
                        </a>
                    </div>
                    <div style="border-top: 1px solid #eee; padding-top: 20px; margin-top: 32px;">
                        <p style="margin: 0; color: #666; font-size: 14px; line-height: 1.4;">
                            인증 링크는 24시간 후 만료됩니다.<br>
                            본인이 요청하지 않았다면 이 메일을 무시해주세요.
                        </p>
                    </div>
                </div>
            </div>
        </body>
        </html>
        """.formatted(verificationUrl);
  }

  // 비밀번호 재설정 이메일 HTML 템플릿
  private String createPasswordResetTemplate(String resetUrl) {
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>비밀번호 재설정</title>
        </head>
        <body style="margin: 0; padding: 0; background-color: #f5f5f5; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;">
            <div style="max-width: 500px; margin: 50px auto; background: white; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 10px rgba(0,0,0,0.1);">
                <div style="background: linear-gradient(135deg, #f093fb 0%%, #f5576c 100%%); padding: 40px 30px; text-align: center;">
                    <h1 style="color: white; margin: 0; font-size: 24px; font-weight: 600;">비밀번호 재설정</h1>
                </div>
                <div style="padding: 40px 30px;">
                    <p style="margin: 0 0 24px 0; color: #333; font-size: 16px; line-height: 1.5;">
                        안녕하세요!<br>
                        비밀번호 재설정을 요청하셨습니다.<br>
                        아래 버튼을 클릭하여 새로운 비밀번호를 설정해주세요.
                    </p>
                    <div style="text-align: center; margin: 32px 0;">
                        <a href="%s" style="display: inline-block; background: #f5576c; color: white; text-decoration: none; padding: 14px 28px; border-radius: 6px; font-weight: 500; font-size: 16px;">
                            비밀번호 재설정하기
                        </a>
                    </div>
                    <div style="border-top: 1px solid #eee; padding-top: 20px; margin-top: 32px;">
                        <p style="margin: 0; color: #666; font-size: 14px; line-height: 1.4;">
                            이 링크는 30분 후 만료됩니다.<br>
                            본인이 요청하지 않았다면 이 메일을 무시해주세요.<br>
                            보안을 위해 링크는 한 번만 사용할 수 있습니다.
                        </p>
                    </div>
                </div>
            </div>
        </body>
        </html>
        """.formatted(resetUrl);
  }
}
