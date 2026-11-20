package com.muscat.commonlib.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@DisplayName("관리자 쓰기 요청 기록")
class AdminAuditFilterTest {

  private final long[] now = {0L};
  private final AdminAuditFilter filter = new AdminAuditFilter(() -> now[0]);
  private ListAppender<ILoggingEvent> appender;
  private Logger logger;

  @BeforeEach
  void attach() {
    logger = (Logger) LoggerFactory.getLogger(AdminAuditFilter.class);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("admin-1").claim("email", "admin@example.com")
      .issuedAt(Instant.EPOCH).expiresAt(Instant.EPOCH.plusSeconds(60)).build();
    SecurityContextHolder.getContext()
      .setAuthentication(new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("admin"))));
  }

  @AfterEach
  void detach() {
    logger.detachAppender(appender);
    SecurityContextHolder.clearContext();
  }

  private static FilterChain responding(int status, long millis, long[] now) {
    return (req, res) -> {
      ((MockHttpServletResponse) res).setStatus(status);
      now[0] += millis * 1_000_000;
    };
  }

  @Test
  @DisplayName("관리자 쓰기 요청은 토큰 사용자 · 메서드 · 경로 · 응답 코드 · 걸린 시간을 한 줄로 남기고 이메일은 남기지 않는다")
  void logsAdminWrite() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/admin/users/42/role");

    filter.doFilter(request, new MockHttpServletResponse(), responding(200, 12, now));

    assertThat(appender.list).singleElement().satisfies(event -> {
      assertThat(event.getLevel()).isEqualTo(Level.INFO);
      assertThat(event.getFormattedMessage())
        .isEqualTo("관리자 요청: actor=admin-1 PATCH /api/admin/users/42/role status=200 12ms");
    });
  }

  @Test
  @DisplayName("실패한 관리자 쓰기 요청도 응답 코드와 같이 경고로 남긴다")
  void warnsFailedAdminWrite() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/admin/market/assets/AAPL");

    filter.doFilter(request, new MockHttpServletResponse(), responding(404, 3, now));

    assertThat(appender.list).singleElement().satisfies(event -> {
      assertThat(event.getLevel()).isEqualTo(Level.WARN);
      assertThat(event.getFormattedMessage()).contains("DELETE /api/admin/market/assets/AAPL status=404");
    });
  }

  @Test
  @DisplayName("요청 처리 중 예외가 나면 500 으로 남기고 예외는 그대로 낸다")
  void logsServerErrorOnException() {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/admin/trade/holdings/rebuild");

    assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
      throw new IllegalStateException("boom");
    })).isInstanceOf(IllegalStateException.class).hasMessage("boom");

    assertThat(appender.list).singleElement()
      .satisfies(event -> assertThat(event.getFormattedMessage()).contains("POST /api/admin/trade/holdings/rebuild status=500"));
  }

  @ParameterizedTest(name = "{0} {1}")
  @CsvSource({
    "GET, /api/admin/users",
    "POST, /api/trade/orders",
    "DELETE, /api/administrator/x",
  })
  @DisplayName("관리자 경로의 조회 · 관리자 밖 경로는 남기지 않는다")
  void ignoresOthers(String method, String path) throws Exception {
    filter.doFilter(new MockHttpServletRequest(method, path), new MockHttpServletResponse(), responding(200, 1, now));

    assertThat(appender.list).isEmpty();
  }

  @Test
  @DisplayName("인증 정보가 없으면 행위자를 익명으로 남긴다")
  void anonymousActor() throws Exception {
    SecurityContextHolder.clearContext();

    filter.doFilter(new MockHttpServletRequest("POST", "/api/admin/trade/splits/backfill"), new MockHttpServletResponse(),
      responding(202, 1, now));

    assertThat(appender.list).singleElement()
      .satisfies(event -> assertThat(event.getFormattedMessage()).startsWith("관리자 요청: actor=anonymous POST"));
  }

  @Test
  @DisplayName("보안 체인이 막은 관리자 쓰기 요청은 경고 한 줄로 남기고, 조회 · 관리자 밖 경로는 남기지 않는다")
  void logRejected_OnlyAdminWrites() {
    AdminAuditFilter.logRejected(new MockHttpServletRequest("DELETE", "/api/admin/users/42"), 403);
    AdminAuditFilter.logRejected(new MockHttpServletRequest("GET", "/api/admin/users"), 401);
    AdminAuditFilter.logRejected(new MockHttpServletRequest("POST", "/api/trade/orders"), 401);

    assertThat(appender.list).singleElement().satisfies(event -> {
      assertThat(event.getLevel()).isEqualTo(Level.WARN);
      assertThat(event.getFormattedMessage())
        .isEqualTo("관리자 요청: actor=admin-1 DELETE /api/admin/users/42 status=403 보안 체인에서 막음");
    });
  }
}
