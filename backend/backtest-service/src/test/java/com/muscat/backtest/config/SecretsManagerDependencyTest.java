package com.muscat.backtest.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.ClassUtils;

// backtest-service 시크릿 출처. Secrets Manager 를 직접 부르지 않음
class SecretsManagerDependencyTest {

  @Test
  @DisplayName("Secrets Manager 연동 라이브러리가 클래스패스에 없다")
  void noSecretsManagerClient() {
    assertThat(ClassUtils.isPresent("io.awspring.cloud.secretsmanager.SecretsManagerPropertySource",
        getClass().getClassLoader())).isFalse();
  }

  @Test
  @DisplayName("설정 파일이 Secrets Manager 를 import 하지 않는다")
  void noSecretsManagerImport() throws IOException {
    String yml = new ClassPathResource("application.yml").getContentAsString(StandardCharsets.UTF_8);

    assertThat(yml).doesNotContain("aws-secretsmanager");
  }
}
