package com.muscat.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.ClassUtils;

// 게이트웨이 의존성. 설정 키는 새 이름으로만 읽음
class GatewayDependencyTest {

  @Test
  @DisplayName("기동 때 옛 설정 키를 새 이름으로 옮기는 도구가 클래스패스에 없다")
  void noPropertiesMigrator() {
    assertThat(ClassUtils.isPresent(
        "org.springframework.boot.context.properties.migrator.PropertiesMigrationListener",
        getClass().getClassLoader())).isFalse();
  }
}
