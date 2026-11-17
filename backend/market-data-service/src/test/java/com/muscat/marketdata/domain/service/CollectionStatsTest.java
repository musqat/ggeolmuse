package com.muscat.marketdata.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
@DisplayName("수집 집계")
class CollectionStatsTest {

  private CollectionStats stats;
  private SimpleMeterRegistry registry;
  private Logger logger;
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void setUp() {
    registry = new SimpleMeterRegistry();
    stats = new CollectionStats(registry);
    logger = (Logger) LoggerFactory.getLogger(CollectionStats.class);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    logger.setLevel(Level.INFO);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(appender);
  }

  private double count(String name, String tag, String value) {
    return registry.get(name).tag(tag, value).counter().count();
  }

  @Test
  @DisplayName("수집 결과를 지표로도 세고, 완료로 보면 마지막 완료 시각을 남긴다")
  void metrics_countResultsAndLastCompleted() {
    stats.recordSymbol(3, 1);
    stats.recordSymbolEmpty();
    stats.recordSymbolFailed();
    stats.recordSplitResync();
    stats.recordDividends(2);
    stats.recordRequestNotFound();
    stats.recordRequestHttpError();
    stats.recordRequestUnreachable();

    stats.report();
    assertThat(registry.get("marketdata.collection.last.completed").gauge().value()).isZero();
    stats.report();

    assertThat(count("marketdata.collection.symbols", "result", "ok")).isEqualTo(1);
    assertThat(count("marketdata.collection.symbols", "result", "empty")).isEqualTo(1);
    assertThat(count("marketdata.collection.symbols", "result", "failed")).isEqualTo(1);
    assertThat(count("marketdata.collection.symbols", "result", "split_resync")).isEqualTo(1);
    assertThat(count("marketdata.collection.candles", "change", "inserted")).isEqualTo(3);
    assertThat(count("marketdata.collection.candles", "change", "updated")).isEqualTo(1);
    assertThat(registry.get("marketdata.collection.dividends").counter().count()).isEqualTo(2);
    assertThat(count("marketdata.collection.requests.failed", "reason", "not_found")).isEqualTo(1);
    assertThat(count("marketdata.collection.requests.failed", "reason", "http_error")).isEqualTo(1);
    assertThat(count("marketdata.collection.requests.failed", "reason", "unreachable")).isEqualTo(1);
    assertThat(registry.get("marketdata.collection.last.completed").gauge().value()).isPositive();
  }

  private List<String> messages() {
    return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
  }

  @Test
  @DisplayName("수집이 없으면 아무것도 남기지 않는다")
  void report_noActivity_logsNothing() {
    stats.report();
    stats.report();

    assertThat(messages()).isEmpty();
  }

  @Test
  @DisplayName("종목 수가 늘면 진행으로 남긴다")
  void report_whileCollecting_logsProgress() {
    stats.recordSymbol(10, 5);
    stats.report();

    stats.recordSymbol(3, 0);
    stats.report();

    assertThat(messages()).hasSize(2);
    assertThat(messages()).allMatch(m -> m.startsWith("수집 진행"));
    assertThat(messages().get(1)).contains("종목 2").contains("신규 13").contains("갱신 5");
  }

  @Test
  @DisplayName("한 주기 동안 종목 수가 그대로면 완료로 남기고 초기화한다")
  void report_whenIdle_logsDoneAndResets() {
    stats.recordSymbol(10, 5);
    stats.recordSymbolEmpty();
    stats.recordSymbolFailed();
    stats.report();

    stats.report();

    assertThat(messages()).hasSize(2);
    assertThat(messages().get(1))
      .startsWith("수집 완료")
      .contains("종목 3").contains("성공 1").contains("빈값 1").contains("실패 1");

    // 초기화 뒤라 다음 주기에는 남길 것 없음
    appender.list.clear();
    stats.report();
    assertThat(messages()).isEmpty();
  }

  @Test
  @DisplayName("요청 실패는 404 와 연결 실패를 나눠 센다")
  void report_countsRequestFailuresByKind() {
    stats.recordSymbol(1, 0);
    stats.recordRequestNotFound();
    stats.recordRequestNotFound();
    stats.recordRequestUnreachable();
    stats.recordRequestHttpError();
    stats.recordDividends(4);

    stats.report();

    assertThat(messages().get(0))
      .contains("배당 4")
      .contains("404 2").contains("HTTP오류 1").contains("연결실패 1");
  }

  @Test
  @DisplayName("건수가 0 인 항목은 줄에서 뺀다")
  void report_omitsZeroCounters() {
    stats.recordSymbol(3, 0);

    stats.report();

    assertThat(messages().get(0))
      .isEqualTo("수집 진행  0분  종목 1  성공 1  신규 3")
      .doesNotContain("갱신").doesNotContain("빈값").doesNotContain("404");
  }

  @Test
  @DisplayName("분할 재수집은 종목으로 세고 따로 표시한다")
  void report_countsSplitResync() {
    stats.recordSplitResync();

    stats.report();

    assertThat(messages().get(0)).isEqualTo("수집 진행  0분  종목 1  분할재수집 1");
  }
}
