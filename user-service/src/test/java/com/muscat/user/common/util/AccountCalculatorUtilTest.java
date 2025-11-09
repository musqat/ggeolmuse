package com.muscat.user.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.muscat.commonlib.exception.ServiceException;
import com.muscat.user.domain.account.dto.response.ExchangeCalculationResult;
import com.muscat.user.domain.account.entity.Account;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountCalculatorUtil 단위 테스트")
class AccountCalculatorUtilTest {

  @InjectMocks
  private AccountCalculatorUtil accountCalculatorUtil;

  private Account testAccount;
  private BigDecimal testExchangeRate;

  @BeforeEach
  void setUp() {
    testAccount = Account.builder()
      .balanceKrw(new BigDecimal("1000000"))
      .balanceUsd(new BigDecimal("1000"))
      .totalExchangedKrw(new BigDecimal("1300000"))
      .build();

    testExchangeRate = new BigDecimal("1300");
  }

  @Nested
  @DisplayName("USD 가치 계산 테스트")
  class CalculateUsdValueInKrwTests {

    @Test
    @DisplayName("정상적인 USD 가치 계산")
    void calculateUsdValue_Success() {
      // given
      Account account = Account.builder()
        .balanceUsd(new BigDecimal("1000"))
        .build();
      BigDecimal rate = new BigDecimal("1300");

      // when
      BigDecimal result = accountCalculatorUtil.calculateUsdValueInKrw(account, rate);

      // then
      assertThat(result).isEqualByComparingTo(new BigDecimal("1300000"));
    }

    @Test
    @DisplayName("USD 잔액이 0인 경우")
    void calculateUsdValue_ZeroBalance() {
      // given
      Account account = Account.builder()
        .balanceUsd(BigDecimal.ZERO)
        .build();

      // when
      BigDecimal result = accountCalculatorUtil.calculateUsdValueInKrw(account, testExchangeRate);

      // then
      assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("USD 잔액이 null인 경우")
    void calculateUsdValue_NullBalance() {
      // given
      Account account = Account.builder().build();

      // when
      BigDecimal result = accountCalculatorUtil.calculateUsdValueInKrw(account, testExchangeRate);

      // then
      assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("환율이 null인 경우")
    void calculateUsdValue_NullRate() {
      // given
      Account account = Account.builder()
        .balanceUsd(new BigDecimal("1000"))
        .build();

      // when
      BigDecimal result = accountCalculatorUtil.calculateUsdValueInKrw(account, null);

      // then
      assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }
  }

  @Nested
  @DisplayName("총 자산 가치 계산 테스트")
  class CalculateTotalValueInKrwTests {

    @Test
    @DisplayName("KRW + USD 총 자산 계산")
    void calculateTotalValue_Success() {
      // given
      Account account = Account.builder()
        .balanceKrw(new BigDecimal("1000000"))
        .balanceUsd(new BigDecimal("1000"))
        .build();
      BigDecimal rate = new BigDecimal("1300");

      // when
      BigDecimal result = accountCalculatorUtil.calculateTotalValueInKrw(account, rate);

      // then - 1,000,000 + (1,000 * 1,300) = 2,300,000
      assertThat(result).isEqualByComparingTo(new BigDecimal("2300000"));
    }

    @Test
    @DisplayName("KRW만 있는 경우")
    void calculateTotalValue_OnlyKrw() {
      // given
      Account account = Account.builder()
        .balanceKrw(new BigDecimal("1000000"))
        .balanceUsd(BigDecimal.ZERO)
        .build();

      // when
      BigDecimal result = accountCalculatorUtil.calculateTotalValueInKrw(account, testExchangeRate);

      // then
      assertThat(result).isEqualByComparingTo(new BigDecimal("1000000"));
    }

    @Test
    @DisplayName("USD만 있는 경우")
    void calculateTotalValue_OnlyUsd() {
      // given
      Account account = Account.builder()
        .balanceKrw(BigDecimal.ZERO)
        .balanceUsd(new BigDecimal("1000"))
        .build();
      BigDecimal rate = new BigDecimal("1300");

      // when
      BigDecimal result = accountCalculatorUtil.calculateTotalValueInKrw(account, rate);

      // then
      assertThat(result).isEqualByComparingTo(new BigDecimal("1300000"));
    }
  }

  @Nested
  @DisplayName("환전 요청 검증 테스트")
  class ValidateExchangeRequestTests {

    @Test
    @DisplayName("정상적인 KRW to USD 환전 요청")
    void validateExchangeRequest_KrwToUsd_Success() {
      // given
      Account account = Account.builder()
        .balanceKrw(new BigDecimal("1000000"))
        .build();
      BigDecimal amount = new BigDecimal("100000");

      // when & then - 예외 발생하지 않음
      accountCalculatorUtil.validateExchangeRequest(account, amount, "KRW");
    }

    @Test
    @DisplayName("정상적인 USD to KRW 환전 요청")
    void validateExchangeRequest_UsdToKrw_Success() {
      // given
      Account account = Account.builder()
        .balanceUsd(new BigDecimal("1000"))
        .build();
      BigDecimal amount = new BigDecimal("100");

      // when & then - 예외 발생하지 않음
      accountCalculatorUtil.validateExchangeRequest(account, amount, "USD");
    }

    @Test
    @DisplayName("잔액 부족 시 예외 발생")
    void validateExchangeRequest_InsufficientBalance_ThrowsException() {
      // given
      Account account = Account.builder()
        .balanceKrw(new BigDecimal("50000"))
        .build();
      BigDecimal amount = new BigDecimal("100000");

      // when & then
      assertThatThrownBy(() ->
        accountCalculatorUtil.validateExchangeRequest(account, amount, "KRW"))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("잔액");
    }

    @Test
    @DisplayName("음수 금액 시 예외 발생")
    void validateExchangeRequest_NegativeAmount_ThrowsException() {
      // given
      BigDecimal negativeAmount = new BigDecimal("-1000");

      // when & then
      assertThatThrownBy(() ->
        accountCalculatorUtil.validateExchangeRequest(testAccount, negativeAmount, "KRW"))
        .isInstanceOf(ServiceException.class);
    }
  }

  @Nested
  @DisplayName("환전 계산 테스트")
  class CalculateExchangeWithCommissionTests {

    @Test
    @DisplayName("KRW to USD 환전 계산")
    void calculateExchange_KrwToUsd() {
      // given
      BigDecimal amount = new BigDecimal("1300000");
      BigDecimal rate = new BigDecimal("1300");

      // when
      ExchangeCalculationResult result = accountCalculatorUtil.calculateExchangeWithCommission(
        testAccount, amount, "KRW", "USD", rate);

      // then
      assertThat(result).isNotNull();
      assertThat(result.getFromCurrency()).isEqualTo("KRW");
      assertThat(result.getToCurrency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("USD to KRW 환전 계산")
    void calculateExchange_UsdToKrw() {
      // given
      BigDecimal amount = new BigDecimal("1000");
      BigDecimal rate = new BigDecimal("1300");

      // when
      ExchangeCalculationResult result = accountCalculatorUtil.calculateExchangeWithCommission(
        testAccount, amount, "USD", "KRW", rate);

      // then
      assertThat(result).isNotNull();
      assertThat(result.getFromCurrency()).isEqualTo("USD");
      assertThat(result.getToCurrency()).isEqualTo("KRW");
    }

    @Test
    @DisplayName("지원하지 않는 환전 방향 시 예외 발생")
    void calculateExchange_UnsupportedDirection_ThrowsException() {
      // when & then
      assertThatThrownBy(() ->
        accountCalculatorUtil.calculateExchangeWithCommission(
          testAccount, BigDecimal.TEN, "EUR", "USD", testExchangeRate))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("지원하지 않는 환전");
    }
  }

  @Nested
  @DisplayName("평균 환율 계산 테스트")
  class CalculateNewAverageRateTests {

    @Test
    @DisplayName("추가 환전 시 평균 환율 재계산")
    void calculateNewAverageRate_Success() {
      // given
      Account account = Account.builder()
        .balanceUsd(new BigDecimal("1000"))
        .totalExchangedKrw(new BigDecimal("1300000"))
        .build();

      BigDecimal newKrwAmount = new BigDecimal("1400000");
      BigDecimal newRate = new BigDecimal("1400");

      // when
      BigDecimal newAvgRate = accountCalculatorUtil.calculateNewAverageRate(
        account, newKrwAmount, newRate);

      // then
      // 기존: 1,300,000 KRW / 1,000 USD = 1,300
      // 추가: 1,400,000 KRW / 1,000 USD = 1,400
      // 총계: 2,700,000 KRW / 2,000 USD = 1,350
      assertThat(newAvgRate).isEqualByComparingTo(new BigDecimal("1350.00"));
    }

    @Test
    @DisplayName("USD 잔액이 0인 경우 평균 환율 0 반환")
    void calculateNewAverageRate_ZeroUsd() {
      // given
      Account account = Account.builder()
        .balanceUsd(BigDecimal.ZERO)
        .totalExchangedKrw(BigDecimal.ZERO)
        .build();

      BigDecimal newKrwAmount = new BigDecimal("1300000");
      BigDecimal newRate = new BigDecimal("1300");

      // when
      BigDecimal newAvgRate = accountCalculatorUtil.calculateNewAverageRate(
        account, newKrwAmount, newRate);

      // then
      assertThat(newAvgRate).isEqualByComparingTo(newRate);
    }

    @Test
    @DisplayName("처음 환전하는 경우 입력 환율을 평균 환율로 설정")
    void calculateNewAverageRate_FirstExchange() {
      // given
      Account account = Account.builder()
        .balanceUsd(BigDecimal.ZERO)
        .totalExchangedKrw(BigDecimal.ZERO)
        .build();

      BigDecimal krwAmount = new BigDecimal("1300000");
      BigDecimal rate = new BigDecimal("1300");

      // when
      BigDecimal avgRate = accountCalculatorUtil.calculateNewAverageRate(
        account, krwAmount, rate);

      // then
      assertThat(avgRate).isEqualByComparingTo(rate);
    }
  }

  @Nested
  @DisplayName("통화별 잔액 조회 테스트")
  class GetBalanceByCurrencyTests {

    @Test
    @DisplayName("KRW 잔액 조회")
    void getBalanceByCurrency_Krw() {
      // given
      Account account = Account.builder()
        .balanceKrw(new BigDecimal("1000000"))
        .build();

      // when
      BigDecimal balance = accountCalculatorUtil.getBalanceByCurrency(account, "KRW");

      // then
      assertThat(balance).isEqualByComparingTo(new BigDecimal("1000000"));
    }

    @Test
    @DisplayName("USD 잔액 조회")
    void getBalanceByCurrency_Usd() {
      // given
      Account account = Account.builder()
        .balanceUsd(new BigDecimal("1000"))
        .build();

      // when
      BigDecimal balance = accountCalculatorUtil.getBalanceByCurrency(account, "USD");

      // then
      assertThat(balance).isEqualByComparingTo(new BigDecimal("1000"));
    }

    @Test
    @DisplayName("소문자 통화 코드도 처리")
    void getBalanceByCurrency_LowerCase() {
      // given
      Account account = Account.builder()
        .balanceKrw(new BigDecimal("1000000"))
        .build();

      // when
      BigDecimal balance = accountCalculatorUtil.getBalanceByCurrency(account, "krw");

      // then
      assertThat(balance).isEqualByComparingTo(new BigDecimal("1000000"));
    }

    @Test
    @DisplayName("지원하지 않는 통화 시 예외 발생")
    void getBalanceByCurrency_UnsupportedCurrency_ThrowsException() {
      // when & then
      assertThatThrownBy(() ->
        accountCalculatorUtil.getBalanceByCurrency(testAccount, "EUR"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("지원하지 않는 통화");
    }

    @Test
    @DisplayName("null 잔액인 경우 0 반환")
    void getBalanceByCurrency_NullBalance() {
      // given
      Account account = Account.builder().build();

      // when
      BigDecimal krwBalance = accountCalculatorUtil.getBalanceByCurrency(account, "KRW");
      BigDecimal usdBalance = accountCalculatorUtil.getBalanceByCurrency(account, "USD");

      // then
      assertThat(krwBalance).isEqualByComparingTo(BigDecimal.ZERO);
      assertThat(usdBalance).isEqualByComparingTo(BigDecimal.ZERO);
    }
  }
}
