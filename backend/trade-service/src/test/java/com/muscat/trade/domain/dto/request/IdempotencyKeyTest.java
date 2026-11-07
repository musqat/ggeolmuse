package com.muscat.trade.domain.dto.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.muscat.trade.common.enums.response.TradeResponse;
import com.muscat.trade.common.enums.type.PriceType;
import com.muscat.trade.common.exception.TradeException;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("중복 요청 키 · 요청 지문")
class IdempotencyKeyTest {

  private static final LocalDate DAY = LocalDate.of(2026, 11, 6);

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
      .isInstanceOf(TradeException.class)
      .extracting("code").isEqualTo(TradeResponse.INVALID_IDEMPOTENCY_KEY);
    assertThatThrownBy(() -> IdempotencyKey.parse("a".repeat(65)))
      .isInstanceOf(TradeException.class)
      .extracting("code").isEqualTo(TradeResponse.INVALID_IDEMPOTENCY_KEY);
  }

  @Test
  @DisplayName("지문은 주문 값으로 정해지고 소수 끝 0 · 빈 직접 가격도 같은 값으로 본다")
  void fingerprint() {
    String buy = IdempotencyKey.fingerprint("BUY", 1L, "AAPL", new BigDecimal("10"), DAY, PriceType.CLOSE, null);

    assertThat(buy).hasSize(64).matches("[0-9a-f]+");
    assertThat(IdempotencyKey.fingerprint("BUY", 1L, "AAPL", new BigDecimal("10.0"), DAY, PriceType.CLOSE, null))
      .isEqualTo(buy);
    assertThat(IdempotencyKey.fingerprint("SELL", 1L, "AAPL", new BigDecimal("10"), DAY, PriceType.CLOSE, null))
      .isNotEqualTo(buy);
    assertThat(IdempotencyKey.fingerprint("BUY", 1L, "AAPL", new BigDecimal("11"), DAY, PriceType.CLOSE, null))
      .isNotEqualTo(buy);
    assertThat(IdempotencyKey.fingerprint("BUY", 1L, "AAPL", new BigDecimal("10"), DAY.plusDays(1), PriceType.CLOSE, null))
      .isNotEqualTo(buy);
    assertThat(IdempotencyKey.fingerprint("BUY", 1L, "AAPL", new BigDecimal("10"), DAY, PriceType.OPEN, null))
      .isNotEqualTo(buy);
  }
}
