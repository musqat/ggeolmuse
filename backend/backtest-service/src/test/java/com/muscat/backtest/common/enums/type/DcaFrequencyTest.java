package com.muscat.backtest.common.enums.type;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("적립식 주기의 매수 이유")
class DcaFrequencyTest {

  @ParameterizedTest(name = "{0} · {1}개월 · {2}주 → {3}")
  @CsvSource({
    "DAILY, 3, 2, 매일 2주",
    "WEEKLY, 1, 1, 매주 1주",
    "MONTHLY, 1, 2, 매달 2주",
    "MONTHLY, 3, 1, 3개월마다 1주"})
  @DisplayName("주식 수로 사면 주기와 주식 수를 남기고, 투자 주기는 매달에서만 쓴다")
  void sharesTrigger(DcaFrequency frequency, int interval, int shares, String trigger) {
    assertThat(frequency.trigger(shares, interval)).isEqualTo(trigger);
  }
}
