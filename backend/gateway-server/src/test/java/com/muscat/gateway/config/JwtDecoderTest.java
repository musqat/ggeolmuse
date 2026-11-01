package com.muscat.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

// Keycloak hostname 이 있으면(운영 · compose) iss 가 공개 주소, 없으면(helm dev) 내부 주소(서명은 이 테스트가 띄운 JWKS 의 키)
class JwtDecoderTest {

  private static final String PUBLIC_ISSUER = "https://example.test/auth/realms/r";
  private static final String INTERNAL_ISSUER = "http://keycloak:8080/auth/realms/r";
  private static final List<String> CLIENT_IDS = List.of("web-client", "app-client");

  private static RSAKey key;
  private static HttpServer jwks;
  private static ReactiveJwtDecoder decoder;

  @BeforeAll
  static void startJwks() throws JOSEException, IOException {
    key = new RSAKeyGenerator(2048).keyID("k1").generate();
    byte[] body = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
    jwks = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    jwks.createContext("/certs", exchange -> {
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, body.length);
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(body);
      }
    });
    jwks.start();
    decoder = SecurityConfig.buildJwtDecoder("http://127.0.0.1:" + jwks.getAddress().getPort() + "/certs",
        List.of(PUBLIC_ISSUER, INTERNAL_ISSUER), CLIENT_IDS);
  }

  @AfterAll
  static void stopJwks() {
    jwks.stop(0);
  }

  @Test
  @DisplayName("허용한 두 issuer 와 두 클라이언트의 토큰은 통과한다")
  void allowedIssuersAndClientsPass() throws JOSEException {
    for (String issuer : List.of(PUBLIC_ISSUER, INTERNAL_ISSUER)) {
      for (String clientId : CLIENT_IDS) {
        assertThat(decoder.decode(token(issuer, clientId)).block().getSubject()).isEqualTo("user-1");
      }
    }
  }

  @Test
  @DisplayName("다른 realm 이나 다른 주소가 발급한 토큰과 issuer 가 없는 토큰은 막는다")
  void otherIssuerRejected() throws JOSEException {
    String otherRealm = token("http://keycloak:8080/auth/realms/other", "app-client");
    String noIssuer = token(null, "app-client");

    assertThatThrownBy(() -> decoder.decode(otherRealm).block()).isInstanceOf(JwtValidationException.class);
    assertThatThrownBy(() -> decoder.decode(noIssuer).block()).isInstanceOf(JwtValidationException.class);
  }

  @Test
  @DisplayName("같은 realm 의 다른 클라이언트로 받은 토큰과 azp 가 없는 토큰은 막는다")
  void otherClientRejected() throws JOSEException {
    String adminCli = token(INTERNAL_ISSUER, "admin-cli");
    String noClient = token(INTERNAL_ISSUER, null);

    assertThatThrownBy(() -> decoder.decode(adminCli).block()).isInstanceOf(JwtValidationException.class);
    assertThatThrownBy(() -> decoder.decode(noClient).block()).isInstanceOf(JwtValidationException.class);
  }

  @Test
  @DisplayName("만료된 토큰은 그대로 막는다")
  void expiredRejected() throws JOSEException {
    String expired = token(INTERNAL_ISSUER, "app-client", Instant.now().minusSeconds(300));

    assertThatThrownBy(() -> decoder.decode(expired).block()).isInstanceOf(JwtValidationException.class);
  }

  private String token(String issuer, String clientId) throws JOSEException {
    return token(issuer, clientId, Instant.now().plusSeconds(300));
  }

  private String token(String issuer, String clientId, Instant expiresAt) throws JOSEException {
    JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder().subject("user-1").expirationTime(Date.from(expiresAt));
    if (issuer != null) {
      claims.issuer(issuer);
    }
    if (clientId != null) {
      claims.claim("azp", clientId);
    }
    SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
        claims.build());
    jwt.sign(new RSASSASigner(key));
    return jwt.serialize();
  }
}
