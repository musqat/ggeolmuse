package com.muscat.user.domain.account.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("계좌 생성 요청 검증")
class CreateAccountRequestDtoTest {

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void setUp() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void tearDown() {
    factory.close();
  }

  private static CreateAccountRequestDto request(BigDecimal slippageRate) {
    CreateAccountRequestDto request = new CreateAccountRequestDto();
    request.setAccountName("신규계좌");
    request.setCommissionRate(new BigDecimal("0.001"));
    request.setSlippageRate(slippageRate);
    return request;
  }

  @Test
  @DisplayName("슬리피지율은 비워도 된다")
  void slippageRate_Null_IsValid() {
    Set<ConstraintViolation<CreateAccountRequestDto>> violations = validator.validate(request(null));

    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("슬리피지율이 1% 를 넘으면 거절한다")
  void slippageRate_OverOnePercent_IsRejected() {
    Set<ConstraintViolation<CreateAccountRequestDto>> violations =
      validator.validate(request(new BigDecimal("0.011")));

    assertThat(violations)
      .extracting(violation -> violation.getPropertyPath().toString())
      .containsExactly("slippageRate");
  }
}
