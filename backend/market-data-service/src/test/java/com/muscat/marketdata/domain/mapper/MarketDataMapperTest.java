package com.muscat.marketdata.domain.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.marketdata.domain.dto.CandleDto;
import com.muscat.marketdata.domain.dto.DividendDto;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.entity.Candle;
import com.muscat.marketdata.domain.entity.Dividend;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("MarketDataMapper 단위 테스트")
class MarketDataMapperTest {

  private static final LocalDate DATE = LocalDate.of(2024, 9, 16);

  private static CandleDto.CandleDtoBuilder candle() {
    return CandleDto.builder()
      .symbol("AAPL")
      .date(DATE)
      .open(new BigDecimal("216.54"))
      .high(new BigDecimal("217.22"))
      .low(new BigDecimal("213.92"))
      .close(new BigDecimal("216.32"))
      .adjustedClose(new BigDecimal("215.30"))
      .volume(59357400L)
      .currency("USD");
  }

  private static DividendDto.DividendDtoBuilder dividend() {
    return DividendDto.builder()
      .symbol("AAPL")
      .exDate(DATE)
      .amount(new BigDecimal("0.25"))
      .currency("USD");
  }

  @Nested
  @DisplayName("toAsset")
  class ToAsset {

    @Test
    @DisplayName("심볼은 대문자, 이름은 앞뒤 공백만 제거한다")
    void 정규화() {
      Asset asset = MarketDataMapper.toAsset("  aapl  ", "  Apple Inc.  ");

      assertThat(asset.getSymbol()).isEqualTo("AAPL");
      assertThat(asset.getName()).isEqualTo("Apple Inc.");
    }

    @Test
    @DisplayName("미국 주식 기본값을 채운다")
    void 기본값() {
      Asset asset = MarketDataMapper.toAsset("AAPL", "Apple Inc.");

      assertThat(asset.getCountry()).isEqualTo("US");
      assertThat(asset.getCurrency()).isEqualTo("USD");
      assertThat(asset.getAssetType()).isEqualTo("EQUITY");
    }

    @Test
    @DisplayName("이름을 안 주면 심볼 대문자를 이름으로 쓴다")
    void 이름_생략() {
      Asset asset = MarketDataMapper.toAsset("aapl");

      assertThat(asset.getSymbol()).isEqualTo("AAPL");
      assertThat(asset.getName()).isEqualTo("AAPL");
    }
  }

  @Nested
  @DisplayName("toCandle")
  class ToCandle {

    @Test
    @DisplayName("정상 DTO 를 그대로 옮긴다")
    void 정상() {
      Candle result = MarketDataMapper.toCandle(candle().build(), null);

      assertThat(result.getSymbol()).isEqualTo("AAPL");
      assertThat(result.getDate()).isEqualTo(DATE);
      assertThat(result.getOpen()).isEqualByComparingTo("216.54");
      assertThat(result.getHigh()).isEqualByComparingTo("217.22");
      assertThat(result.getLow()).isEqualByComparingTo("213.92");
      assertThat(result.getClose()).isEqualByComparingTo("216.32");
      assertThat(result.getAdjustedClose()).isEqualByComparingTo("215.30");
      assertThat(result.getVolume()).isEqualTo(59357400L);
      assertThat(result.getCurrency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("배당·분할 필드는 고정값으로 채운다")
    void 고정_필드() {
      Candle result = MarketDataMapper.toCandle(candle().build(), null);

      assertThat(result.getDividendAmount()).isEqualByComparingTo("0");
      assertThat(result.getSplitCoefficient()).isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("dto 가 null 이면 null")
    void dto_null() {
      assertThat(MarketDataMapper.toCandle(null, "AAPL")).isNull();
    }

    @Test
    @DisplayName("날짜가 없으면 null")
    void 날짜_없음() {
      assertThat(MarketDataMapper.toCandle(candle().date(null).build(), "AAPL")).isNull();
    }

    @Test
    @DisplayName("OHLCV 중 하나라도 없으면 null")
    void ohlcv_결손() {
      assertThat(MarketDataMapper.toCandle(candle().open(null).build(), "AAPL")).isNull();
      assertThat(MarketDataMapper.toCandle(candle().high(null).build(), "AAPL")).isNull();
      assertThat(MarketDataMapper.toCandle(candle().low(null).build(), "AAPL")).isNull();
      assertThat(MarketDataMapper.toCandle(candle().close(null).build(), "AAPL")).isNull();
      assertThat(MarketDataMapper.toCandle(candle().volume(null).build(), "AAPL")).isNull();
    }

    @Test
    @DisplayName("adjustedClose 가 없으면 close 로 채운다")
    void adjclose_대체() {
      Candle result = MarketDataMapper.toCandle(candle().adjustedClose(null).build(), null);

      assertThat(result.getAdjustedClose()).isEqualByComparingTo("216.32");
    }

    @Test
    @DisplayName("통화가 없으면 USD")
    void 통화_기본값() {
      Candle result = MarketDataMapper.toCandle(candle().currency(null).build(), null);

      assertThat(result.getCurrency()).isEqualTo("USD");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("dto 심볼이 비면 fallback 을 쓴다")
    void fallback_사용(String dtoSymbol) {
      Candle result = MarketDataMapper.toCandle(candle().symbol(dtoSymbol).build(), " msft ");

      assertThat(result.getSymbol()).isEqualTo("MSFT");
    }

    @Test
    @DisplayName("dto 심볼이 있으면 fallback 보다 우선한다")
    void dto_심볼_우선() {
      Candle result = MarketDataMapper.toCandle(candle().symbol(" aapl ").build(), "MSFT");

      assertThat(result.getSymbol()).isEqualTo("AAPL");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("dto 심볼도 fallback 도 비면 null")
    void 심볼_없음(String blank) {
      assertThat(MarketDataMapper.toCandle(candle().symbol(blank).build(), blank)).isNull();
    }
  }

  @Nested
  @DisplayName("toCandles")
  class ToCandles {

    @Test
    @DisplayName("목록을 통째로 변환한다")
    void 목록_변환() {
      List<Candle> result = MarketDataMapper.toCandles(
        List.of(candle().build(), candle().date(DATE.plusDays(1)).build()), null);

      assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("변환 실패한 건은 빼고 나머지는 살린다")
    void 부분_실패() {
      List<CandleDto> input = Arrays.asList(
        candle().build(),
        candle().open(null).build(),   // OHLCV 결손
        null,                          // null 요소
        candle().date(DATE.plusDays(2)).build());

      List<Candle> result = MarketDataMapper.toCandles(input, "AAPL");

      // 한 건이 깨져도 수집 전체가 날아가면 안 된다
      assertThat(result).hasSize(2);
      assertThat(result).extracting(Candle::getDate)
        .containsExactly(DATE, DATE.plusDays(2));
    }

    @Test
    @DisplayName("입력이 null 이면 빈 목록")
    void 입력_null() {
      assertThat(MarketDataMapper.toCandles(null, "AAPL")).isEmpty();
    }

    @Test
    @DisplayName("빈 목록이면 빈 목록")
    void 입력_빈목록() {
      assertThat(MarketDataMapper.toCandles(List.of(), "AAPL")).isEmpty();
    }

    @Test
    @DisplayName("전부 깨져 있으면 빈 목록")
    void 전부_실패() {
      List<CandleDto> input = Arrays.asList(null, candle().close(null).build());

      assertThat(MarketDataMapper.toCandles(input, "AAPL")).isEmpty();
    }
  }

  @Nested
  @DisplayName("toDividend")
  class ToDividend {

    @Test
    @DisplayName("정상 DTO 를 그대로 옮긴다")
    void 정상() {
      Dividend result = MarketDataMapper.toDividend(dividend().build());

      assertThat(result.getSymbol()).isEqualTo("AAPL");
      assertThat(result.getExDate()).isEqualTo(DATE);
      assertThat(result.getAmount()).isEqualByComparingTo("0.25");
      assertThat(result.getCurrency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("통화가 없으면 USD")
    void 통화_기본값() {
      Dividend result = MarketDataMapper.toDividend(dividend().currency(null).build());

      assertThat(result.getCurrency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("필수값이 하나라도 없으면 null")
    void 필수값_결손() {
      assertThat(MarketDataMapper.toDividend(null)).isNull();
      assertThat(MarketDataMapper.toDividend(dividend().symbol(null).build())).isNull();
      assertThat(MarketDataMapper.toDividend(dividend().exDate(null).build())).isNull();
      assertThat(MarketDataMapper.toDividend(dividend().amount(null).build())).isNull();
    }

    @Test
    @DisplayName("심볼은 대문자로 바꾸지 않고 그대로 둔다")
    void 심볼_원본_유지() {
      // toAsset/toCandle 과 달리 여기선 정규화하지 않는다
      Dividend result = MarketDataMapper.toDividend(dividend().symbol("aapl").build());

      assertThat(result.getSymbol()).isEqualTo("aapl");
    }
  }

  @Nested
  @DisplayName("toDividends")
  class ToDividends {

    @Test
    @DisplayName("변환 실패한 건은 빼고 나머지는 살린다")
    void 부분_실패() {
      List<DividendDto> input = Arrays.asList(
        dividend().build(),
        dividend().amount(null).build(),
        null,
        dividend().exDate(DATE.plusMonths(3)).build());

      List<Dividend> result = MarketDataMapper.toDividends(input);

      assertThat(result).hasSize(2);
      assertThat(result).extracting(Dividend::getExDate)
        .containsExactly(DATE, DATE.plusMonths(3));
    }

    @Test
    @DisplayName("입력이 null 이면 빈 목록")
    void 입력_null() {
      assertThat(MarketDataMapper.toDividends(null)).isEmpty();
    }

    @Test
    @DisplayName("빈 목록이면 빈 목록")
    void 입력_빈목록() {
      assertThat(MarketDataMapper.toDividends(List.of())).isEmpty();
    }
  }
}
