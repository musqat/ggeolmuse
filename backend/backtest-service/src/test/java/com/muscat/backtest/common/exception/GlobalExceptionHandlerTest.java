package com.muscat.backtest.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.backtest.common.enums.BacktestResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;

@DisplayName("backtest 예외 핸들러 선택")
class GlobalExceptionHandlerTest {

  // 스프링이 advice 마다 만드는 resolver. 같은 예외에 메서드가 둘 걸리면 생성에서 실패한다
  private final ExceptionHandlerMethodResolver resolver =
    new ExceptionHandlerMethodResolver(GlobalExceptionHandler.class);

  @Test
  @DisplayName("서비스 예외는 BOM 에서 상속한 핸들러가, 그 밖의 예외는 서비스 500 핸들러가 받는다")
  void resolvesInheritedBusinessHandler() {
    assertThat(resolver.resolveMethod(new BacktestException(BacktestResponse.INVALID_REQUEST)).getName())
      .isEqualTo("handleBusinessException");
    assertThat(resolver.resolveMethod(new IllegalStateException("원인")).getName())
      .isEqualTo("handleGeneralException");
  }
}
