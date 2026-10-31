package com.muscat.marketdata.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.marketdata.api.controller.AdminMarketController;
import com.muscat.marketdata.api.controller.DataCollectionController;
import com.muscat.marketdata.api.controller.InternalMarketController;
import com.muscat.marketdata.api.controller.MarketController;
import com.muscat.marketdata.common.enums.response.MarketDataResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.servlet.error.BasicErrorController;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;
import org.springframework.web.bind.MissingServletRequestParameterException;
import java.util.List;
import org.springframework.web.method.ControllerAdviceBean;

@DisplayName("market-data 예외 응답 변환")
class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = handlerWithProfiles();

  @Test
  @DisplayName("서비스 예외 · 요청 값 오류는 공통 모듈에서 상속한 핸들러가, 외부 API 예외와 그 밖의 예외는 서비스 핸들러가 받는다")
  void resolvesInheritedHandlers() {
    // 스프링이 advice 마다 만드는 resolver. 같은 예외에 메서드가 둘 걸리면 생성에서 실패한다
    ExceptionHandlerMethodResolver resolver = new ExceptionHandlerMethodResolver(GlobalExceptionHandler.class);

    assertThat(resolver.resolveMethod(new MarketDataException(MarketDataResponse.PRICE_DATA_NOT_FOUND)).getName())
      .isEqualTo("handleBusinessException");
    assertThat(resolver.resolveMethod(new MissingServletRequestParameterException("date", "LocalDate")).getName())
      .isEqualTo("handleMissingParameterException");
    assertThat(resolver.resolveMethod(new HttpMessageNotReadableException("JSON parse error", new MockHttpInputMessage(new byte[0]))).getName())
      .isEqualTo("handleNotReadable");
    assertThat(resolver.resolveMethod(new YahooFinanceException("원인")).getName())
      .isEqualTo("handleYahooFinanceException");
    assertThat(resolver.resolveMethod(new IllegalStateException("원인")).getName())
      .isEqualTo("handleGeneralException");
  }

  @Test
  @DisplayName("MarketDataException 은 상속한 BOM 핸들러가 enum 이름 errorCode 로 바꾼다")
  void handleMarketDataException_UsesEnumName() {
    ResponseEntity<ProblemDetail> response = handler.handleBusinessException(
      new MarketDataException(MarketDataResponse.PRICE_DATA_NOT_FOUND), new MockHttpServletRequest());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    ProblemDetail body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getTitle()).isEqualTo("Not Found");
    assertThat(body.getDetail()).isEqualTo("가격 데이터를 찾을 수 없습니다");
    assertThat(body.getProperties())
      .containsEntry("errorCode", "PRICE_DATA_NOT_FOUND")
      .containsEntry("errorType", "NOT_FOUND");
  }

  // 컨트롤러와 같은 모양의 파라미터 검사. @Validated 가 이 검사로 ConstraintViolationException 을 만든다
  @SuppressWarnings("unused")
  static class SampleController {
    void price(@Pattern(regexp = "^[A-Z]{1,16}$") String symbol, @Positive BigDecimal minAmount) {
    }
  }

  private static ConstraintViolationException violations(Object... args) throws NoSuchMethodException {
    Method method = SampleController.class.getDeclaredMethod("price", String.class, BigDecimal.class);
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      Set<ConstraintViolation<SampleController>> found = factory.getValidator().forExecutables()
        .validateParameters(new SampleController(), method, args);
      return new ConstraintViolationException(found);
    }
  }

  @Test
  @DisplayName("경로 변수 · 쿼리 파라미터 검증 실패는 400 이고 걸린 파라미터 이름을 싣는다")
  void constraintViolation_BadRequest() throws Exception {
    assertThat(new ExceptionHandlerMethodResolver(GlobalExceptionHandler.class).resolveMethod(violations("aapl", BigDecimal.ONE)))
      .extracting(Method::getName).isEqualTo("handleConstraintViolationException");

    ResponseEntity<ProblemDetail> response = handler.handleConstraintViolationException(
      violations("aapl", new BigDecimal("-1")), new MockHttpServletRequest("GET", "/api/market/price/aapl"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    ProblemDetail body = Objects.requireNonNull(response.getBody());
    assertThat(body.getStatus()).isEqualTo(400);
    assertThat(body.getDetail()).isEqualTo("요청 값이 올바르지 않습니다: minAmount, symbol");
    assertThat(body.getProperties()).containsEntry("parameterName", "minAmount, symbol").containsEntry("errorType", "VALIDATION");
  }

  @Test
  @DisplayName("야후 · AlphaVantage · 수출입은행 오류는 502 이고 외부 서비스 오류로 표시한다")
  void externalServiceErrors() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/market/price/AAPL");

    ResponseEntity<ProblemDetail> yahoo = handler.handleYahooFinanceException(new YahooFinanceException("429"), request);
    ResponseEntity<ProblemDetail> av = handler.handleAlphaVantageException(new AlphaVantageException("limit"), request);
    ResponseEntity<ProblemDetail> fx = handler.handleFxRateException(new FxRateException("no data"), request);

    assertThat(List.of(yahoo, av, fx)).allSatisfy(response -> {
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
      assertThat(Objects.requireNonNull(response.getBody()).getProperties()).containsEntry("errorType", "EXTERNAL_SERVICE");
    });
    assertThat(Objects.requireNonNull(yahoo.getBody()).getDetail()).isEqualTo("Yahoo Finance 서비스 오류: 429");
    assertThat(Objects.requireNonNull(av.getBody()).getDetail()).isEqualTo("Alpha Vantage 서비스 오류: limit");
    assertThat(Objects.requireNonNull(fx.getBody()).getDetail()).isEqualTo("한국수출입은행 서비스 오류: no data");
  }

  @Test
  @DisplayName("예상하지 못한 예외는 운영 · 프로필 없음에서 내용을 감춘다")
  void generalException_Production_HidesDetail() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/market/price/AAPL");
    IllegalStateException leak = new IllegalStateException("jdbc:postgresql://db/secret");
    String hidden = "시장 데이터 서비스에 문제가 발생했습니다.";

    ResponseEntity<ProblemDetail> prod = handlerWithProfiles("prod").handleGeneralException(leak, request);
    ResponseEntity<ProblemDetail> none = handlerWithProfiles().handleGeneralException(leak, request);

    assertThat(prod.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(Objects.requireNonNull(prod.getBody()).getDetail()).isEqualTo(hidden);
    assertThat(Objects.requireNonNull(none.getBody()).getDetail()).isEqualTo(hidden);
  }

  private static GlobalExceptionHandler handlerWithProfiles(String... profiles) {
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles(profiles);
    GlobalExceptionHandler handler = new GlobalExceptionHandler();
    handler.setEnvironment(environment);
    return handler;
  }

  // 컨트롤러 패키지 밖에 둔 컨트롤러
  @RestController
  static class TestPackageController {
  }

  @Test
  @DisplayName("서비스 패키지 아래 모든 컨트롤러에 적용되고 스프링 기본 오류 컨트롤러에는 적용되지 않는다")
  void appliesToEveryServiceController() {
    ControllerAdviceBean advice;
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(GlobalExceptionHandler.class)) {
      advice = ControllerAdviceBean.findAnnotatedBeans(context).get(0);
    }

    assertThat(List.of(MarketController.class, DataCollectionController.class, AdminMarketController.class,
      InternalMarketController.class, TestPackageController.class))
      .allMatch(advice::isApplicableToBeanType);
    assertThat(advice.isApplicableToBeanType(BasicErrorController.class)).isFalse();
  }
}
