package com.muscat.user.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.user.api.controller.AdminUserController;
import com.muscat.user.domain.user.dto.request.RegisterRequestDto;
import com.muscat.user.domain.user.dto.request.UpdateProfileRequestDto;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("닉네임 규칙은 가입 · 프로필 · 관리자 변경이 같다")
class NicknameRuleTest {

  private static final String RULE = "닉네임은 2 ~ 20자의 영문, 숫자, 한글, _ 만 쓸 수 있습니다";
  private static final String REQUIRED = "닉네임은 필수입니다";

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

  // 경로 이름 · 닉네임이 꼭 있어야 하는지 · 닉네임만 채운 요청
  static Stream<Arguments> requests() {
    Function<String, Object> register = nickname -> {
      RegisterRequestDto request = new RegisterRequestDto();
      request.setEmail("user@example.com");
      request.setPassword("abcd123!");
      request.setNickname(nickname);
      return request;
    };
    Function<String, Object> profile = nickname -> {
      UpdateProfileRequestDto request = new UpdateProfileRequestDto();
      request.setNickname(nickname);
      return request;
    };
    Function<String, Object> admin = AdminUserController.UpdateNicknameRequest::new;
    return Stream.of(
      Arguments.of("가입", true, register),
      Arguments.of("프로필", false, profile),
      Arguments.of("관리자 변경", true, admin));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("requests")
  @DisplayName("2 ~ 20자의 영문 · 숫자 · 한글 · _ 는 통과한다")
  void valid(String path, boolean required, Function<String, Object> request) {
    for (String nickname : List.of("투자자", "ab", "Investor_01", "가".repeat(20))) {
      assertThat(validator.validate(request.apply(nickname))).as("%s / %s", path, nickname).isEmpty();
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("requests")
  @DisplayName("빈 값 · 1자 · 21자 · 띄어쓰기 · 기호는 규칙 문구 하나로 거절한다")
  void invalid(String path, boolean required, Function<String, Object> request) {
    for (String nickname : List.of("", "a", "가".repeat(21), "투자 자", "투자자!")) {
      assertThat(validator.validate(request.apply(nickname)))
        .as("%s / %s", path, nickname)
        .extracting(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
        .containsExactly("nickname: " + RULE);
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("requests")
  @DisplayName("닉네임이 없으면 가입 · 관리자 변경은 거절하고 프로필은 바꾸지 않는 것으로 본다")
  void missing(String path, boolean required, Function<String, Object> request) {
    if (required) {
      assertThat(validator.validate(request.apply(null)))
        .extracting(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
        .containsExactly("nickname: " + REQUIRED);
    } else {
      assertThat(validator.validate(request.apply(null))).isEmpty();
    }
  }
}
