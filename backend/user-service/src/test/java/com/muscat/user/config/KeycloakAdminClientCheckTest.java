package com.muscat.user.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

@DisplayName("사용자 관리 클라이언트 설정 · 기동 검사")
class KeycloakAdminClientCheckTest {

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(KeycloakAdminClientProperties.class)
  static class PropertiesConfig {
  }

  private static ApplicationContextRunner withProfiles(String... profiles) {
    return new ApplicationContextRunner()
      .withInitializer(context -> context.getEnvironment().setActiveProfiles(profiles))
      .withUserConfiguration(PropertiesConfig.class, KeycloakAdminClientCheck.class);
  }

  @Test
  @DisplayName("운영 프로필에서 secret 이 없으면 기동 실패")
  void prod_WithoutSecret_Fails() {
    withProfiles("prod").run(context -> assertThat(context).hasFailed());
  }

  @Test
  @DisplayName("운영 프로필에서 secret 이 공백이면 기동 실패")
  void prod_BlankSecret_Fails() {
    withProfiles("prod").withPropertyValues("keycloak.admin-client.secret= ")
      .run(context -> assertThat(context).hasFailed());
  }

  @Test
  @DisplayName("운영 프로필에서 secret 이 있으면 기동")
  void prod_WithSecret_Starts() {
    withProfiles("prod").withPropertyValues("keycloak.admin-client.secret=admin-secret")
      .run(context -> assertThat(context).hasNotFailed());
  }

  @Test
  @DisplayName("운영 밖 프로필은 secret 이 없어도 기동")
  void nonProd_WithoutSecret_Starts() {
    withProfiles("dev").run(context -> assertThat(context).hasNotFailed());
  }

  @Test
  @DisplayName("application.yml 의 사용자 관리 클라이언트 이름은 realm 의 ggeolmuse-user-admin")
  void applicationYml_UserAdminClientId() throws IOException {
    List<PropertySource<?>> sources = new YamlPropertySourceLoader()
      .load("application", new ClassPathResource("application.yml"));
    KeycloakAdminClientProperties properties = new Binder(ConfigurationPropertySources.from(sources))
      .bind("keycloak.admin-client", KeycloakAdminClientProperties.class).get();

    assertThat(properties.getId()).isEqualTo("ggeolmuse-user-admin");
  }
}
