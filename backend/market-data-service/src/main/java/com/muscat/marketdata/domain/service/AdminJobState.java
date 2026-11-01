package com.muscat.marketdata.domain.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// 관리자 작업의 진행 중 여부 · 마지막 상태(admin_job_state 테이블로 파드 사이 공유)
@Component
public class AdminJobState {

  // 갱신 시각이 이만큼 지나면 그 파드는 죽은 것으로 봄
  static final Duration STALE_AFTER = Duration.ofMinutes(30);

  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public AdminJobState(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  // 진행 중이 아니거나 갱신이 끊겼으면 진행 중으로 표시(표시했으면 true)
  public boolean tryStart(String name) {
    LocalDateTime now = LocalDateTime.now();
    insertIfAbsent(name, false, null, now);
    return jdbc.update("UPDATE admin_job_state SET running = TRUE, heartbeat_at = ? "
      + "WHERE name = ? AND (running = FALSE OR heartbeat_at < ?)", now, name, now.minus(STALE_AFTER)) == 1;
  }

  // 진행 상태 저장 · 갱신 시각 기록
  public void save(String name, Object detail) {
    jdbc.update("UPDATE admin_job_state SET detail = ?, heartbeat_at = ? WHERE name = ?",
      json(detail), LocalDateTime.now(), name);
  }

  // 끝 표시와 마지막 상태
  public void finish(String name, Object detail) {
    LocalDateTime now = LocalDateTime.now();
    String value = json(detail);
    if (jdbc.update("UPDATE admin_job_state SET running = FALSE, detail = ?, heartbeat_at = ? WHERE name = ?",
      value, now, name) == 0) {
      insertIfAbsent(name, false, value, now);
    }
  }

  public boolean isRunning(String name) {
    LocalDateTime staleBefore = LocalDateTime.now().minus(STALE_AFTER);
    return Boolean.TRUE.equals(jdbc.query("SELECT running, heartbeat_at FROM admin_job_state WHERE name = ?",
      rs -> rs.next() && rs.getBoolean(1) && rs.getTimestamp(2).toLocalDateTime().isAfter(staleBefore), name));
  }

  public <T> Optional<T> detail(String name, Class<T> type) {
    String value = jdbc.query("SELECT detail FROM admin_job_state WHERE name = ?",
      rs -> rs.next() ? rs.getString(1) : null, name);
    if (value == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(mapper.readValue(value, type));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("관리자 작업 상태를 읽지 못함: " + name, e);
    }
  }

  // 두 파드가 같이 넣으면 한쪽은 이미 있는 행
  private void insertIfAbsent(String name, boolean running, String detail, LocalDateTime now) {
    try {
      jdbc.update("INSERT INTO admin_job_state (name, running, heartbeat_at, detail) VALUES (?, ?, ?, ?)",
        name, running, now, detail);
    } catch (DuplicateKeyException ignored) {
      // 이미 있는 행
    }
  }

  private String json(Object detail) {
    try {
      return detail == null ? null : mapper.writeValueAsString(detail);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("관리자 작업 상태를 쓰지 못함", e);
    }
  }
}
