package com.muscat.commonlib.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("느린 요청 경고")
class SlowRequestFilterTest {

  private final long[] now = {0L};
  private final SlowRequestFilter filter = new SlowRequestFilter(() -> now[0]);
  private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/backtest/run");
  private ListAppender<ILoggingEvent> appender;
  private Logger logger;

  @BeforeEach
  void attach() {
    logger = (Logger) LoggerFactory.getLogger(SlowRequestFilter.class);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void detach() {
    logger.detachAppender(appender);
  }

  private FilterChain taking(long millis) {
    return (req, res) -> now[0] += millis * 1_000_000;
  }

  @Test
  @DisplayName("5초를 넘긴 요청은 메서드 · 경로 · 걸린 시간을 경고 한 줄로 남긴다")
  void warnsOverFiveSeconds() throws Exception {
    filter.doFilter(request, new MockHttpServletResponse(), taking(5_001));

    assertThat(appender.list).singleElement().satisfies(event -> {
      assertThat(event.getLevel()).isEqualTo(Level.WARN);
      assertThat(event.getFormattedMessage()).isEqualTo("느린 요청: GET /api/backtest/run 5001ms");
    });
  }

  @Test
  @DisplayName("5초 안에 끝난 요청은 남기지 않는다")
  void silentWithinFiveSeconds() throws Exception {
    filter.doFilter(request, new MockHttpServletResponse(), taking(5_000));

    assertThat(appender.list).isEmpty();
  }

  @Test
  @DisplayName("요청이 예외로 끝나도 5초를 넘기면 경고를 남긴다")
  void warnsWhenChainThrows() {
    FilterChain failing = (req, res) -> {
      now[0] += 6_000L * 1_000_000;
      throw new IllegalStateException("실패");
    };

    assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), failing))
      .isInstanceOf(IllegalStateException.class);
    assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
      .containsExactly("느린 요청: GET /api/backtest/run 6000ms");
  }
}
