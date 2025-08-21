package com.muscat.user.mail;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MailService {

  private final JavaMailSender mailSender;

  @Value("${app.mail.from}")
  private String fromEmail;

  @Value("${app.mail.verification.base-url}")
  private String baseUrl;

  public void sendVerificationEmail(String toEmail, String token) {
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

      helper.setFrom(fromEmail);
      helper.setTo(toEmail);
      helper.setSubject("이메일 인증을 완료해주세요");

      String verificationUrl = baseUrl + "/api/auth/verify-email?token=" + token;
      String htmlContent = String.format(
          "<html><body style='font-family: Arial, sans-serif; line-height: 1.6; color: #333;'>" +
              "<div style='max-width: 600px; margin: 0 auto; padding: 20px;'>" +
              "<h2 style='color: #2c3e50; text-align: center;'>안녕하세요!</h2>" +
              "<p style='font-size: 16px; margin: 20px 0;'>회원가입을 완료하려면 아래 버튼을 클릭해주세요:</p>" +
              "<div style='text-align: center; margin: 30px 0;'>" +
              "<a href='%s' style='background-color: #4CAF50; color: white; padding: 15px 30px; text-decoration: none; border-radius: 5px; font-size: 16px; display: inline-block;'>이메일 인증하기</a>"
              +
              "</div>" +
              "<p style='font-size: 14px; color: #666; margin-top: 30px;'>이 링크는 24시간 후 만료됩니다.</p>" +
              "<p style='font-size: 14px; color: #666;'>감사합니다.</p>" +
              "</div>" +
              "</body></html>",
          verificationUrl
      );

      helper.setText(htmlContent, true);

      mailSender.send(message);
      log.info("Verification email sent to: {}", toEmail);

    } catch (Exception e) {
      log.error("Failed to send verification email to: {}", toEmail, e);
      throw new RuntimeException("Failed to send verification email", e);
    }
  }
}
