package com.muscat.user.config;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

// 실제 logback-spring.xml · application.yml 로 콘솔 JSON 로그를 만들어 이메일 가리기와 추적 ID 를 본다
@DisplayName("로그의 이메일 가리기")
class LogbackEmailMaskingTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  private String encode(LoggingEvent event, LoggerContext context) {
    @SuppressWarnings("unchecked")
    OutputStreamAppender<ILoggingEvent> console =
      (OutputStreamAppender<ILoggingEvent>) context.getLogger("ROOT").getAppender("CONSOLE");
    return new String(console.getEncoder().encode(event), StandardCharsets.UTF_8);
  }

  private LoggerContext context() throws Exception {
    StandardEnvironment environment = new StandardEnvironment();
    environment.getPropertySources().addLast(
      new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml")).get(0));
    LoggerContext context = new LoggerContext();
    context.putObject(Environment.class.getName(), environment);
    JoranConfigurator configurator = new JoranConfigurator();
    configurator.setContext(context);
    configurator.doConfigure(getClass().getResource("/logback-spring.xml"));
    return context;
  }

  private JsonNode log(Level level, String message, Throwable error, Map<String, String> mdc, Object... args)
    throws Exception {
    LoggerContext context = context();
    LoggingEvent event = new LoggingEvent("test", context.getLogger("test"), level, message, error, args);
    event.setMDCPropertyMap(mdc);
    String line = encode(event, context);
    assertThat(line).startsWith("{").endsWith("}\n");
    return JSON.readTree(line);
  }

  private JsonNode info(String message, Object... args) throws Exception {
    return log(Level.INFO, message, null, Map.of(), args);
  }

  // 문의 대응 때 쓰는 명령(printf | tr | sha256sum | cut -c1-12)과 같은 계산
  private static String sha256Prefix(String text) throws Exception {
    byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
    return HexFormat.of().formatHex(hash).substring(0, 12);
  }

  @Test
  @DisplayName("이메일은 앞 한 글자 · 도메인과 이메일 지문(SHA-256 앞 12자리)만 남긴다")
  void masksEmailWithFingerprint() throws Exception {
    JsonNode line = info("로그인 성공: {}", "hjs90561@gmail.com");

    assertThat(line.get("message").asText())
      .isEqualTo("로그인 성공: h***@gmail.com#" + sha256Prefix("hjs90561@gmail.com"));
    assertThat(line.toString()).doesNotContain("hjs90561");
  }

  @Test
  @DisplayName("대소문자가 달라도 같은 지문이라 같은 사람으로 찾을 수 있다")
  void fingerprintIgnoresCase() throws Exception {
    JsonNode upper = info("{}", "HJS90561@Gmail.com");

    assertThat(upper.get("message").asText()).contains("#" + sha256Prefix("hjs90561@gmail.com"));
  }

  @Test
  @DisplayName("한 줄에 여러 개여도 모두 가리고, 객체 문자열 · 다른 내용은 그대로 둔다")
  void masksEveryEmailAndKeepsOtherText() throws Exception {
    JsonNode line = info("from={} to={} obj={} userId={}", "a.b+c@example.com", "zz@test.local", "Account@5f3a", 42);

    assertThat(line.get("message").asText()).isEqualTo(
      "from=a***@example.com#" + sha256Prefix("a.b+c@example.com")
        + " to=z***@test.local#" + sha256Prefix("zz@test.local")
        + " obj=Account@5f3a userId=42");
  }

  @Test
  @DisplayName("예외 메시지 · 스택 속 이메일도 가린다")
  void masksEmailInError() throws Exception {
    JsonNode line = log(Level.ERROR, "가입 실패", new IllegalStateException("이미 있는 이메일: zz@test.local"), Map.of());

    assertThat(line.toString()).contains("z***@test.local#" + sha256Prefix("zz@test.local"))
      .doesNotContain("zz@test.local");
  }

  @Test
  @DisplayName("MDC 의 trace_id · span_id 를 필드로 싣는다")
  void writesTraceIdFromMdc() throws Exception {
    JsonNode line = log(Level.INFO, "요청", null, Map.of("trace_id", "4bf92f3577b34da6a3ce929d0e0e4736",
      "span_id", "00f067aa0ba902b7"));

    assertThat(line.get("trace_id").asText()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
    assertThat(line.get("span_id").asText()).isEqualTo("00f067aa0ba902b7");
  }
}
