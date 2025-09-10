package com.muscat.commonlib.util;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Map;

public final class ProblemDetailUtils {

  private static final String PROBLEM_BASE_URL = "https://api.muscat.com/problems/";

  private ProblemDetailUtils() {
    throw new AssertionError("유틸리티 클래스는 인스턴스화할 수 없습니다");
  }

  // 기본 ProblemDetail 생성
  public static ProblemDetail createProblem(HttpStatus status, String detail, String errorCode, String path) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(URI.create(PROBLEM_BASE_URL + errorCode.toLowerCase().replace("_", "-")));
    problem.setInstance(URI.create(path));
    problem.setProperty("errorCode", errorCode);
    problem.setProperty("timestamp", LocalDateTime.now());
    return problem;
  }

  // 제목 포함 ProblemDetail 생성
  public static ProblemDetail createProblem(HttpStatus status, String detail, String errorCode, String path, String title) {
    ProblemDetail problem = createProblem(status, detail, errorCode, path);
    problem.setTitle(title);
    return problem;
  }

  // 추가 속성 포함 ProblemDetail 생성
  public static ProblemDetail createProblem(HttpStatus status, String detail, String errorCode, String path, String title, Map<String, Object> properties) {
    ProblemDetail problem = createProblem(status, detail, errorCode, path, title);
    if (properties != null) {
      properties.forEach(problem::setProperty);
    }
    return problem;
  }

  // 유효성 검사 오류용 ProblemDetail 생성
  public static ProblemDetail createValidationProblem(String detail, String path, Map<String, String> validationErrors) {
    ProblemDetail problem = createProblem(HttpStatus.BAD_REQUEST, detail, "VALIDATION_ERROR", path, "Validation Error");
    problem.setProperty("validationErrors", validationErrors);
    return problem;
  }

  // 내부 서버 오류용 ProblemDetail 생성
  public static ProblemDetail createInternalServerError(String path) {
    return createProblem(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "서버 내부 오류가 발생했습니다",
        "INTERNAL_SERVER_ERROR",
        path,
        "Internal Server Error"
    );
  }

  // 잘못된 요청용 ProblemDetail 생성
  public static ProblemDetail createBadRequestProblem(String detail, String errorCode, String path) {
    return createProblem(HttpStatus.BAD_REQUEST, detail, errorCode, path, "Bad Request");
  }

  // 리소스를 찾을 수 없음용 ProblemDetail 생성
  public static ProblemDetail createNotFoundProblem(String detail, String errorCode, String path) {
    return createProblem(HttpStatus.NOT_FOUND, detail, errorCode, path, "Not Found");
  }

  // 인증되지 않음용 ProblemDetail 생성
  public static ProblemDetail createUnauthorizedProblem(String detail, String errorCode, String path) {
    return createProblem(HttpStatus.UNAUTHORIZED, detail, errorCode, path, "Unauthorized");
  }

  // 서비스 사용 불가용 ProblemDetail 생성
  public static ProblemDetail createServiceUnavailableProblem(String detail, String errorCode, String path, String provider) {
    ProblemDetail problem = createProblem(HttpStatus.SERVICE_UNAVAILABLE, detail, errorCode, path, "Service Unavailable");
    problem.setProperty("provider", provider);
    return problem;
  }
}