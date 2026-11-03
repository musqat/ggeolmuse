package com.muscat.commonlib.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import feign.Request;
import feign.RetryableException;
import feign.Retryer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("서비스 공통 Feign 설정")
class FeignConfigTest {

  private static RetryableException connectFailure() {
    Request request = Request.create(Request.HttpMethod.GET, "http://market-data-service:8083/api/internal/market/fx/latest",
      Map.of(), null, StandardCharsets.UTF_8, null);
    return new RetryableException(-1, "Connection refused", Request.HttpMethod.GET, (Long) null, request);
  }

  @Test
  @DisplayName("연결이 실패해도 Feign 은 다시 보내지 않는다")
  void retryer_PropagatesFirstFailure() {
    Retryer retryer = new FeignConfig().retryer();
    RetryableException failure = connectFailure();

    assertThatThrownBy(() -> retryer.continueOrPropagate(failure)).isSameAs(failure);
  }
}
