package com.muscat.backtest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

// 서비스 spring.jackson 설정으로 Boot 가 만드는 JsonMapper
public final class ServiceJson {

  private static final JsonMapper MAPPER = build();

  private ServiceJson() {
  }

  public static JsonMapper mapper() {
    return MAPPER;
  }

  private static JsonMapper build() {
    AtomicReference<JsonMapper> mapper = new AtomicReference<>();
    new ApplicationContextRunner()
      .withInitializer(new ConfigDataApplicationContextInitializer())
      .withPropertyValues("spring.profiles.active=test")
      .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
      .run(context -> {
        assertThat(context).hasNotFailed();
        mapper.set(context.getBean(JsonMapper.class));
      });
    return mapper.get();
  }
}
