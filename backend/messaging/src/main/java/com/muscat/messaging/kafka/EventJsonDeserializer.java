package com.muscat.messaging.kafka;

import org.springframework.kafka.support.JacksonMapperUtils;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

// Kafka 이벤트 JSON 읽기
public class EventJsonDeserializer<T> extends JacksonJsonDeserializer<T> {

  static final JsonMapper MAPPER = JacksonMapperUtils.enhancedJsonMapper().rebuild()
    .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
    .build();

  public EventJsonDeserializer() {
    super(MAPPER);
  }

  public EventJsonDeserializer(Class<? super T> type) {
    super(type, MAPPER, false);
  }
}
