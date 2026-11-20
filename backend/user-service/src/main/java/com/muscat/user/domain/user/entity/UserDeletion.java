package com.muscat.user.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

// 탈퇴한 사용자 하나의 남은 정리 기록(Keycloak ID 만, 이메일 없음)
@Entity
@Table(name = "user_deletions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserDeletion implements Persistable<String> {

  @Id
  private String keycloakId;

  @Column(nullable = false)
  private LocalDateTime deletedAt;

  private LocalDateTime eventPublishedAt;

  private LocalDateTime keycloakDeletedAt;

  // save 가 merge 대신 persist 로 INSERT
  @Transient
  private boolean newEntity = true;

  public static UserDeletion of(String keycloakId, LocalDateTime deletedAt) {
    UserDeletion deletion = new UserDeletion();
    deletion.keycloakId = keycloakId;
    deletion.deletedAt = deletedAt;
    return deletion;
  }

  public void markEventPublished(LocalDateTime at) {
    this.eventPublishedAt = at;
  }

  public void markKeycloakDeleted(LocalDateTime at) {
    this.keycloakDeletedAt = at;
  }

  public boolean isDone() {
    return eventPublishedAt != null && keycloakDeletedAt != null;
  }

  @Override
  public String getId() {
    return keycloakId;
  }

  @Override
  public boolean isNew() {
    return newEntity;
  }

  @PostLoad
  @PostPersist
  void markPersisted() {
    this.newEntity = false;
  }
}
