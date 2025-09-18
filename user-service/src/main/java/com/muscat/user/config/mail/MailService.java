package com.muscat.user.config.mail;

import com.muscat.user.common.exceptions.UserException;
import com.muscat.user.common.enums.responses.UserResponse;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MailService {

  private final JavaMailSender mailSender;

  public MailService(JavaMailSender mailSender) {
    this.mailSender = mailSender;
    log.info("MailService 초기화 - JavaMailSender: 정상");
  }

  @Value("${app.mail.from:noreply@example.com}")
  private String fromEmail;

  @Value("${app.mail.verification.base-url:http://localhost:8090}")
  private String baseUrl;

  // 이메일 인증 메일 발송
  public void sendVerificationEmail(String toEmail, String token) {
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

      helper.setFrom(fromEmail);
      helper.setTo(toEmail);
      helper.setSubject("이메일 인증을 완료해주세요");

      String verificationUrl = baseUrl + "/api/auth/verify-email?token=" + token;
      String htmlContent = createEmailTemplate(verificationUrl);

      helper.setText(htmlContent, true);
      mailSender.send(message);

      log.info("이메일 인증 메일 발송 완료: {}", toEmail);

    } catch (Exception e) {
      log.error("이메일 발송 실패 (임시로 무시): {}", toEmail, e);
      // 임시로 에러를 무시하고 계속 진행
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
}
