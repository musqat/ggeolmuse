package com.muscat.user.common.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 로그 메시지 속 이메일을 가리고 지문을 붙인다. h***@gmail.com#(SHA-256 앞 12자리)
// 같은 이메일은 같은 지문이라, 문의가 오면 그 이메일로 지문을 만들어 로그를 찾는다:
// 도메인에 점이 있을 때만 이메일로 본다(Account@5f3a 같은 객체 문자열은 그대로)
public class MaskedEmailMessageConverter extends ClassicConverter {

  private static final Pattern EMAIL = Pattern.compile("([\\w.+-])[\\w.+-]*@([\\w-]+(?:\\.[\\w-]+)+)");
  private static final int FINGERPRINT_LENGTH = 12;

  @Override
  public String convert(ILoggingEvent event) {
    return mask(event.getFormattedMessage());
  }

  static String mask(String message) {
    if (message == null || message.indexOf('@') < 0) {
      return message;
    }
    Matcher matcher = EMAIL.matcher(message);
    StringBuilder masked = new StringBuilder();
    while (matcher.find()) {
      String replacement = matcher.group(1) + "***@" + matcher.group(2) + "#" + fingerprint(matcher.group());
      matcher.appendReplacement(masked, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(masked);
    return masked.toString();
  }

  static String fingerprint(String email) {
    try {
      byte[] hash = MessageDigest.getInstance("SHA-256")
        .digest(email.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash).substring(0, FINGERPRINT_LENGTH);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
