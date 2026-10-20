package com.muscat.marketdata.datasource.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("종목 유형 판정")
class AssetTypeRuleTest {

  @ParameterizedTest
  @ValueSource(strings = {"SPDR S&P 500 ETF Trust", "GRANITESHARES YIELDBOOST AMD ETF", "Some Exchange Traded Fund",
      "JPMorgan 100 U.S. Treasury Securities Money Market", "ProShares GENIUS Money Market"})
  @DisplayName("이름에 ETF 낱말 · exchange traded fund · money market 이 있으면 출처가 주식이라 해도 ETF")
  void fundNames(String name) {
    assertThat(AssetTypeRule.isFundName(name)).isTrue();
    assertThat(AssetTypeRule.resolve(false, name)).isEqualTo("ETF");
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"Netflix Inc", "Betfair Group", "Apple Inc", "ETFMG Holdings"})
  @DisplayName("낱말 안에 든 etf 나 펀드 말이 없는 이름은 주식")
  void stockNames(String name) {
    assertThat(AssetTypeRule.isFundName(name)).isFalse();
    assertThat(AssetTypeRule.resolve(false, name)).isEqualTo("EQUITY");
  }

  @Test
  @DisplayName("출처가 ETF 라 하면 이름과 상관없이 ETF")
  void sourceSaysEtf() {
    assertThat(AssetTypeRule.resolve(true, "Invesco QQQ Trust")).isEqualTo("ETF");
  }
}
