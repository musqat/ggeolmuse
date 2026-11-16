package com.muscat.commonlib.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// 서명은 이 테스트가 띄운 JWKS 의 키, iss 는 공개 주소 · 내부 주소 둘
@WebMvcTest(controllers = ServiceJwtDecoderTest.Probe.class)
@ContextConfiguration(classes = {ServiceJwtDecoderTest.SecurityChain.class, ServiceJwtDecoderTest.Probe.class})
@DisplayName("서비스가 받는 토큰의 발급자 · 클라이언트 · 이메일 인증 검사")
class ServiceJwtDecoderTest {

  private static final String REALM = "r";
  private static final String PUBLIC_ISSUER = "https://example.test/auth/realms/" + REALM;
  private static RSAKey key;
  private static HttpServer keycloak;

  @Autowired
  private MockMvc mockMvc;

  @BeforeAll
  static void startJwks() throws JOSEException, IOException {
    key = new RSAKeyGenerator(2048).keyID("k1").generate();
    byte[] body = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
    keycloak = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    keycloak.createContext("/realms/" + REALM + "/protocol/openid-connect/certs", exchange -> {
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, body.length);
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(body);
      }
    });
    keycloak.start();
  }

  @AfterAll
  static void stopJwks() {
    keycloak.stop(0);
  }

  private static String authServerUrl() {
    return "http://127.0.0.1:" + keycloak.getAddress().getPort();
  }

  private static String internalIssuer() {
    return authServerUrl() + "/realms/" + REALM;
  }

  private static MockEnvironment environment() {
    return new MockEnvironment()
      .withProperty("KEYCLOAK_AUTH_SERVER_URL", authServerUrl())
      .withProperty("KEYCLOAK_REALM", REALM);
  }

  // 발급자 · 클라이언트 허용 목록을 준 디코더로 모든 요청을 인증
  @Configuration
  @EnableWebSecurity
  static class SecurityChain {

    @Bean
    JwtDecoder jwtDecoder() {
      return ServiceSecurity.keycloakJwtDecoder(environment()
        .withProperty("keycloak.issuers", PUBLIC_ISSUER + "," + internalIssuer())
        .withProperty("keycloak.client-ids", "web-client, app-client"));
    }

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
      ServiceSecurity.statelessApi(http)
        .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
      return http.build();
    }
  }

  @RestController
  static class Probe {

    @GetMapping("/api/things")
    String ok() {
      return "ok";
    }
  }

  @Test
  @DisplayName("허용한 두 발급자와 두 클라이언트의 토큰은 통과한다")
  void allowedIssuersAndClientsPass() throws Exception {
    for (String issuer : new String[] {PUBLIC_ISSUER, internalIssuer()}) {
      for (String clientId : new String[] {"web-client", "app-client"}) {
        mockMvc.perform(get("/api/things").header("Authorization", "Bearer " + token(issuer, clientId)))
          .andExpect(status().isOk());
      }
    }
  }

  @Test
  @DisplayName("허용하지 않은 realm 이 발급한 토큰과 발급자가 없는 토큰은 401")
  void otherIssuerUnauthorized() throws Exception {
    String otherRealm = token(authServerUrl() + "/realms/other", "app-client");
    String noIssuer = token(null, "app-client");

    mockMvc.perform(get("/api/things").header("Authorization", "Bearer " + otherRealm))
      .andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/things").header("Authorization", "Bearer " + noIssuer))
      .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("같은 realm 의 허용하지 않은 클라이언트로 받은 토큰과 azp 가 없는 토큰은 401")
  void otherClientUnauthorized() throws Exception {
    String adminCli = token(internalIssuer(), "admin-cli");
    String noClient = token(internalIssuer(), null);

    mockMvc.perform(get("/api/things").header("Authorization", "Bearer " + adminCli))
      .andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/things").header("Authorization", "Bearer " + noClient))
      .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("이메일 인증 전 사용자의 토큰과 email_verified 가 없는 토큰은 401")
  void unverifiedEmailUnauthorized() throws Exception {
    String unverified = token(internalIssuer(), "app-client", false);
    String noClaim = token(internalIssuer(), "app-client", null);

    mockMvc.perform(get("/api/things").header("Authorization", "Bearer " + unverified))
      .andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/things").header("Authorization", "Bearer " + noClaim))
      .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("허용 목록이 없으면 내부 주소의 realm 발급자와 로그인 클라이언트 둘만 받는다")
  void defaultsToInternalIssuerAndLoginClients() throws JOSEException {
    JwtDecoder decoder = ServiceSecurity.keycloakJwtDecoder(environment());

    assertThat(decoder.decode(token(internalIssuer(), "ggeolmuse-client")).getSubject()).isEqualTo("user-1");
    assertThat(decoder.decode(token(internalIssuer(), "ggeolmuse-frontend")).getSubject()).isEqualTo("user-1");
    assertThatThrownBy(() -> decoder.decode(token(PUBLIC_ISSUER, "ggeolmuse-client")))
      .isInstanceOf(JwtValidationException.class);
    assertThatThrownBy(() -> decoder.decode(token(internalIssuer(), "admin-cli")))
      .isInstanceOf(JwtValidationException.class);
  }

  private static String token(String issuer, String clientId) throws JOSEException {
    return token(issuer, clientId, true);
  }

  private static String token(String issuer, String clientId, Boolean emailVerified) throws JOSEException {
    JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder().subject("user-1")
      .expirationTime(Date.from(Instant.now().plusSeconds(300)));
    if (issuer != null) {
      claims.issuer(issuer);
    }
    if (clientId != null) {
      claims.claim("azp", clientId);
    }
    if (emailVerified != null) {
      claims.claim("email_verified", emailVerified);
    }
    SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
      claims.build());
    jwt.sign(new RSASSASigner(key));
    return jwt.serialize();
  }
}
