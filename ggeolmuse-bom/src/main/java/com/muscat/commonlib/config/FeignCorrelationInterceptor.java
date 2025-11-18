package com.muscat.commonlib.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Feign Client 상관관계 ID 전파 인터셉터
 */
@Component
@Slf4j
public class FeignCorrelationInterceptor implements RequestInterceptor {

  /**
   * 상관관계 ID를 전달하는 HTTP 헤더 이름
   */
  private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

  /**
   * MDC에서 상관관계 ID를 가져올 키 이름
   */
  private static final String CORRELATION_ID_KEY = "correlationId";

  /**
   * Feign 요청 실행 전 호출되는 메서드
   *
   * <p>MDC에서 상관관계 ID를 가져와 요청 헤더에 추가합니다.
   * 상관관계 ID가 없는 경우 (비동기 작업 등) 헤더를 추가하지 않습니다.
   *
   * @param template Feign 요청 템플릿 (헤더, URL 등 설정)
   */
  @Override
  public void apply(RequestTemplate template) {
    // MDC에서 현재 스레드의 상관관계 ID 가져오기
    String correlationId = MDC.get(CORRELATION_ID_KEY);

    if (correlationId != null && !correlationId.trim().isEmpty()) {
      // 요청 헤더에 상관관계 ID 추가
      template.header(CORRELATION_ID_HEADER, correlationId);

      log.trace("Feign request correlation ID added: url={}, correlationId={}",
        template.url(), correlationId);
    } else {
      // 상관관계 ID가 없는 경우 (일반적으로 발생하지 않아야 함)
      log.debug("No correlation ID found in MDC for Feign request: url={}",
        template.url());
    }
  }
}
