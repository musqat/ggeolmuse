package com.muscat.user.domain.account.dto.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.muscat.user.common.enums.response.AccountResponse;
import com.muscat.user.common.exception.AccountException;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("중복 요청 키 · 요청 지문")
class IdempotencyKeyTest {

  @Test
  @DisplayName("헤더가 없으면 키가 없다")
  void parse_NoHeader() {
    assertThat(IdempotencyKey.parse(null)).isNull();
  }

  @Test
  @DisplayName("UUID 와 64자 영문 · 숫자 · 하이픈은 키가 된다")
  void parse_Valid() {
    assertThat(IdempotencyKey.parse("3f2b8c1e-9d4a-4e6f-8a7b-1c2d3e4f5a6b").value())
      .isEqualTo("3f2b8c1e-9d4a-4e6f-8a7b-1c2d3e4f5a6b");
    assertThat(IdempotencyKey.parse("a".repeat(64)).value()).hasSize(64);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "키", "abc def", "abc_def", "abc/def"})
  @DisplayName("헤더가 비었거나 65자이거나 영문 · 숫자 · 하이픈 밖의 글자가 있으면 400 이다")
  void parse_Invalid(String header) {
    assertThatThrownBy(() -> IdempotencyKey.parse(header))
      .isInstanceOf(AccountException.class)
      .extracting("code").isEqualTo(AccountResponse.INVALID_IDEMPOTENCY_KEY);
    assertThatThrownBy(() -> IdempotencyKey.parse("a".repeat(65)))
      .isInstanceOf(AccountException.class)
      .extracting("code").isEqualTo(AccountResponse.INVALID_IDEMPOTENCY_KEY);
  }

  @Test
  @DisplayName("지문은 작업 이름과 값으로 정해지고 소수 끝 0 은 보지 않는다")
  void fingerprint() {
    String deposit = IdempotencyKey.fingerprint("DEPOSIT_KRW", 3L, new BigDecimal("100000"));

    assertThat(deposit).hasSize(64).matches("[0-9a-f]+");
    assertThat(IdempotencyKey.fingerprint("DEPOSIT_KRW", 3L, new BigDecimal("100000.00"))).isEqualTo(deposit);
    assertThat(IdempotencyKey.fingerprint("DEPOSIT_KRW", 3L, new BigDecimal("100001"))).isNotEqualTo(deposit);
    assertThat(IdempotencyKey.fingerprint("DEPOSIT_KRW", 4L, new BigDecimal("100000"))).isNotEqualTo(deposit);
    assertThat(IdempotencyKey.fingerprint("EXCHANGE_KRW_TO_USD", 3L, new BigDecimal("100000"))).isNotEqualTo(deposit);
  }
}
