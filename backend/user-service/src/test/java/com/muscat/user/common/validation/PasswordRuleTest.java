package com.muscat.user.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.user.api.controller.AdminUserController;
import com.muscat.user.domain.user.dto.request.ChangePasswordRequestDto;
import com.muscat.user.domain.user.dto.request.RegisterRequestDto;
import com.muscat.user.domain.user.dto.request.ResetPasswordRequestDto;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("새 비밀번호 규칙은 가입 · 변경 · 재설정 · 관리자 변경이 같다")
class PasswordRuleTest {

  private static final String RULE = "비밀번호는 8 ~ 100자이고 영문, 숫자, 특수문자를 각각 하나 이상 포함해야 합니다";

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

  // 경로 이름 · 새 비밀번호 칸 이름 · 그 칸만 채운 요청
  static Stream<Arguments> requests() {
    Function<String, Object> register = password -> {
      RegisterRequestDto request = new RegisterRequestDto();
      request.setEmail("user@example.com");
      request.setPassword(password);
      request.setNickname("투자자");
      return request;
    };
    Function<String, Object> change = password -> {
      ChangePasswordRequestDto request = new ChangePasswordRequestDto();
      request.setCurrentPassword("Old-pass1!");
      request.setNewPassword(password);
      return request;
    };
    Function<String, Object> reset = password -> {
      ResetPasswordRequestDto request = new ResetPasswordRequestDto();
      request.setToken("token");
      request.setNewPassword(password);
      return request;
    };
    Function<String, Object> admin = AdminUserController.UpdatePasswordRequest::new;
    return Stream.of(
      Arguments.of("가입", "password", register),
      Arguments.of("변경", "newPassword", change),
      Arguments.of("재설정", "newPassword", reset),
      Arguments.of("관리자 변경", "newPassword", admin));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("requests")
  @DisplayName("8 ~ 100자에 영문 · 숫자 · 특수문자가 하나씩 있으면 통과한다")
  void valid(String path, String field, Function<String, Object> request) {
    assertThat(validator.validate(request.apply("abcd123!"))).isEmpty();
    assertThat(validator.validate(request.apply("a1!" + "x".repeat(97)))).isEmpty();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("requests")
  @DisplayName("빈 값 · 7자 · 101자 · 특수문자 없음 · 숫자 없음은 규칙 문구 하나로 거절한다")
  void invalid(String path, String field, Function<String, Object> request) {
    for (String password : Arrays.asList(null, "", "abc123!", "a1!" + "x".repeat(98), "abcd1234", "abcdefg!")) {
      assertThat(validator.validate(request.apply(password)))
        .as("%s / %s", path, password)
        .extracting(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
        .containsExactly(field + ": " + RULE);
    }
  }
}
