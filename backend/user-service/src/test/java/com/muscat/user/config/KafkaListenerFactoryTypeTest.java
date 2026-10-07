package com.muscat.user.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

// 컨테이너 팩토리가 역직렬화하는 값 타입이 리스너 @Payload 와 다르면 리스너를 못 불러 첫 시도에 DLT 로 간다
@DisplayName("리스너와 컨테이너 팩토리의 값 타입")
class KafkaListenerFactoryTypeTest {

  @Test
  @DisplayName("모든 @KafkaListener 의 @Payload 타입이 containerFactory 의 값 타입과 같다")
  void payloadTypeMatchesFactory() throws Exception {
    ClassPathScanningCandidateComponentProvider scanner =
      new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));

    List<String> mismatches = new ArrayList<>();
    int checked = 0;
    for (BeanDefinition definition : scanner.findCandidateComponents("com.muscat.user")) {
      for (Method method : Class.forName(definition.getBeanClassName()).getDeclaredMethods()) {
        KafkaListener listener = method.getAnnotation(KafkaListener.class);
        if (listener == null) {
          continue;
        }
        checked++;
        Class<?> payload = payloadType(method);
        Type factoryType = KafkaConsumerConfig.class.getMethod(listener.containerFactory())
          .getGenericReturnType();
        Type valueType = ((ParameterizedType) factoryType).getActualTypeArguments()[1];
        if (!valueType.equals(payload)) {
          mismatches.add(method.getDeclaringClass().getSimpleName() + "." + method.getName() + ": "
            + payload.getSimpleName() + " != " + valueType.getTypeName());
        }
      }
    }

    assertThat(checked).isGreaterThanOrEqualTo(8);
    assertThat(mismatches).isEmpty();
  }

  private static Class<?> payloadType(Method method) {
    for (Parameter parameter : method.getParameters()) {
      if (parameter.isAnnotationPresent(Payload.class)) {
        return parameter.getType();
      }
    }
    throw new IllegalStateException("@Payload 없음: " + method);
  }
}
