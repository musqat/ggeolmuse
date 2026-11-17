package com.muscat.gateway.config;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 경로 · 메서드마다 익명 · 일반 사용자 · 관리자 토큰이 받는 응답(라우팅 앞 보안 체인만, 통과하면 테스트 핸들러가 200)
@WebFluxTest(controllers = SecurityConfigAuthorizationTest.AnyPath.class)
@Import({SecurityConfig.class, SecurityConfigAuthorizationTest.AnyPath.class})
@DisplayName("게이트웨이 경로별 인가")
class SecurityConfigAuthorizationTest {

  @RestController
  static class AnyPath {

    @RequestMapping("/**")
    String ok() {
      return "ok";
    }
  }

  @Autowired
  private WebTestClient client;

  enum Caller { ANONYMOUS, USER, ADMIN }

  static Stream<Arguments> rules() {
    return Stream.of(
      // 로그인 · 가입 · 문서 · 헬스는 누구나
      Arguments.of("POST", "/api/auth/login", Caller.ANONYMOUS, 200),
      Arguments.of("GET", "/actuator/health", Caller.ANONYMOUS, 200),
      Arguments.of("GET", "/v3/api-docs", Caller.ANONYMOUS, 200),
      // 시세 조회 · 여러 날짜 환율은 누구나, 그 밖의 시세 쓰기는 토큰 필요
      Arguments.of("GET", "/api/market/stocks", Caller.ANONYMOUS, 200),
      Arguments.of("POST", "/api/market/fx/bulk", Caller.ANONYMOUS, 200),
      Arguments.of("POST", "/api/market/stocks", Caller.ANONYMOUS, 401),
      // 백테스트 시뮬레이션 · 분석 실행(POST)은 누구나, 같은 경로의 조회는 토큰 필요
      Arguments.of("POST", "/api/trading-simulation/run", Caller.ANONYMOUS, 200),
      Arguments.of("POST", "/api/analysis/compare", Caller.ANONYMOUS, 200),
      Arguments.of("GET", "/api/analysis/compare", Caller.ANONYMOUS, 401),
      // 개인 경로는 토큰 필요
      Arguments.of("GET", "/api/users/me", Caller.ANONYMOUS, 401),
      Arguments.of("GET", "/api/users/me", Caller.USER, 200),
      Arguments.of("GET", "/api/accounts", Caller.ANONYMOUS, 401),
      Arguments.of("POST", "/api/trade/orders", Caller.ANONYMOUS, 401),
      Arguments.of("POST", "/api/trade/orders", Caller.USER, 200),
      Arguments.of("GET", "/api/portfolio", Caller.ANONYMOUS, 401),
      Arguments.of("GET", "/api/backtest/history", Caller.ANONYMOUS, 401),
      Arguments.of("GET", "/api/backtest/history", Caller.USER, 200),
      // 관리자 경로는 admin 권한만(조회 · 쓰기 모두)
      Arguments.of("GET", "/api/admin/users", Caller.ANONYMOUS, 401),
      Arguments.of("GET", "/api/admin/users", Caller.USER, 403),
      Arguments.of("GET", "/api/admin/users", Caller.ADMIN, 200),
      Arguments.of("PATCH", "/api/admin/users/1/role", Caller.USER, 403),
      Arguments.of("PATCH", "/api/admin/users/1/role", Caller.ADMIN, 200),
      Arguments.of("GET", "/api/admin/market/stocks", Caller.USER, 403),
      // 목록에 없는 경로도 토큰 필요
      Arguments.of("GET", "/api/unknown", Caller.ANONYMOUS, 401),
      Arguments.of("GET", "/api/unknown", Caller.USER, 200)
    );
  }

  @ParameterizedTest(name = "{0} {1} · {2} → {3}")
  @MethodSource("rules")
  @DisplayName("경로 · 메서드 · 호출자마다 정한 응답")
  void authorization(String method, String path, Caller caller, int status) {
    WebTestClient as = switch (caller) {
      case ANONYMOUS -> client;
      case USER -> client.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("user")));
      case ADMIN -> client.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("user"), new SimpleGrantedAuthority("admin")));
    };

    as.method(HttpMethod.valueOf(method)).uri(path).exchange().expectStatus().isEqualTo(status);
  }

  static Stream<Arguments> preflights() {
    return Stream.of(
      // 허용 출처는 토큰이 필요한 경로도 preflight 통과(토큰은 본 요청에서 봄)
      Arguments.of("/api/users/me", "http://localhost:3000", 200),
      Arguments.of("/api/auth/login", "http://localhost:5173", 200),
      // 목록에 없는 출처는 거절
      Arguments.of("/api/market/stocks", "https://evil.example", 403)
    );
  }

  @ParameterizedTest(name = "OPTIONS {0} · {1} → {2}")
  @MethodSource("preflights")
  @DisplayName("CORS preflight 는 허용 출처만 통과")
  void preflight(String path, String origin, int status) {
    // 같은 출처인지 가리려면 요청 주소에 호스트가 있어야 함(없으면 출처와 상관없이 403)
    WebTestClient.ResponseSpec response = client.options().uri("http://gateway.local" + path)
      .header("Origin", origin)
      .header("Access-Control-Request-Method", "GET")
      .exchange()
      .expectStatus().isEqualTo(status);

    if (status == 200) {
      response.expectHeader().valueEquals("Access-Control-Allow-Origin", origin);
    } else {
      response.expectHeader().doesNotExist("Access-Control-Allow-Origin");
    }
  }
}
