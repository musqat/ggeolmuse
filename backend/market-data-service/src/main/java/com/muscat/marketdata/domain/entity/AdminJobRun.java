package com.muscat.marketdata.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "admin_job_run")
public class AdminJobRun {

  @Id
  @Column(name = "name", nullable = false, length = 64)
  private String name;

  // 서울 시각
  @Column(name = "last_run_at", nullable = false)
  private LocalDateTime lastRunAt;

  @Column(name = "published", nullable = false)
  private int published;
}
