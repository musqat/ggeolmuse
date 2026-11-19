package com.muscat.commonlib.util;

import com.muscat.commonlib.exception.CommonResponse;
import com.muscat.commonlib.exception.ErrorCode;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

public final class ProblemDetailUtils {

  private static final String PROBLEM_BASE_URL = "https://api.muscat.com/problems/";

  private ProblemDetailUtils() {
  }

  // 오류 코드의 상태 · 이름 · 문구로 만든 ProblemDetail
  public static ProblemDetail createProblem(ErrorCode code, String path, String title, Map<String, Object> properties) {
    return createProblem(code.getHttpStatus(), code.getMessage(), code.name(), path, title, properties);
  }

  // 기본 ProblemDetail 생성
  public static ProblemDetail createProblem(HttpStatus status, String detail, String errorCode,
                                            String path) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(URI.create(PROBLEM_BASE_URL + errorCode.toLowerCase().replace("_", "-")));
    problem.setInstance(URI.create(path));
    problem.setProperty("errorCode", errorCode);
    problem.setProperty("timestamp", LocalDateTime.now());
    return problem;
  }

  // 제목 포함 ProblemDetail 생성
  public static ProblemDetail createProblem(HttpStatus status, String detail, String errorCode,
                                            String path, String title) {
    ProblemDetail problem = createProblem(status, detail, errorCode, path);
    problem.setTitle(title);
    return problem;
  }

  // 추가 속성 포함 ProblemDetail 생성
  public static ProblemDetail createProblem(HttpStatus status, String detail, String errorCode,
                                            String path, String title, Map<String, Object> properties) {
    ProblemDetail problem = createProblem(status, detail, errorCode, path, title);
    if (properties != null) {
      properties.forEach(problem::setProperty);
    }
    return problem;
  }

  // 본문 검증 실패 ProblemDetail(필드별 문구는 validationErrors 에)
  public static ProblemDetail createValidationProblem(String path, Map<String, String> validationErrors) {
    ProblemDetail problem = createProblem(CommonResponse.VALIDATION_ERROR, path, "Validation Error", Map.of());
    problem.setProperty("validationErrors", validationErrors);
    return problem;
  }
}
