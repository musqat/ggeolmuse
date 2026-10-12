package com.muscat.user.config;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 실제 logback-spring.xml 을 읽어 콘솔 로그에서 이메일이 가려지고 지문이 붙는지 본다
@DisplayName("로그의 이메일 가리기")
class LogbackEmailMaskingTest {

  private String format(String message, Object... args) throws Exception {
    LoggerContext context = new LoggerContext();
    JoranConfigurator configurator = new JoranConfigurator();
    configurator.setContext(context);
    configurator.doConfigure(getClass().getResource("/logback-spring.xml"));
    @SuppressWarnings("unchecked")
    OutputStreamAppender<ILoggingEvent> console =
      (OutputStreamAppender<ILoggingEvent>) context.getLogger("ROOT").getAppender("CONSOLE");
    LoggingEvent event = new LoggingEvent("test", context.getLogger("test"), Level.INFO, message, null, args);
    return new String(console.getEncoder().encode(event), StandardCharsets.UTF_8);
  }

  // 문의 대응 때 쓰는 명령(printf | tr | sha256sum | cut -c1-12)과 같은 계산
  private static String sha256Prefix(String text) throws Exception {
    byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
    return HexFormat.of().formatHex(hash).substring(0, 12);
  }

  @Test
  @DisplayName("이메일은 앞 한 글자 · 도메인과 이메일 지문(SHA-256 앞 12자리)만 남긴다")
  void masksEmailWithFingerprint() throws Exception {
    String line = format("로그인 성공: {}", "hjs90561@gmail.com");

    assertThat(line).contains("로그인 성공: h***@gmail.com#" + sha256Prefix("hjs90561@gmail.com"))
      .doesNotContain("hjs90561");
  }

  @Test
  @DisplayName("대소문자가 달라도 같은 지문이라 같은 사람으로 찾을 수 있다")
  void fingerprintIgnoresCase() throws Exception {
    String upper = format("{}", "HJS90561@Gmail.com");

    assertThat(upper).contains("#" + sha256Prefix("hjs90561@gmail.com"));
  }

  @Test
  @DisplayName("한 줄에 여러 개여도 모두 가리고, 객체 문자열 · 다른 내용은 그대로 둔다")
  void masksEveryEmailAndKeepsOtherText() throws Exception {
    String line = format("from={} to={} obj={} userId={}", "a.b+c@example.com", "zz@test.local", "Account@5f3a", 42);

    assertThat(line).contains(
      "from=a***@example.com#" + sha256Prefix("a.b+c@example.com")
        + " to=z***@test.local#" + sha256Prefix("zz@test.local")
        + " obj=Account@5f3a userId=42");
  }
}
