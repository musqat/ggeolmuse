package com.muscat.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.commonlib.exception.BaseException;
import com.muscat.commonlib.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@DisplayName("패키지 이름 규칙")
class PackageLayoutTest {

  private static final String BASE = "com.muscat.user";

  @Test
  @DisplayName("컨트롤러는 api.controller 나 기능 패키지의 controller 에 있다")
  void controllers() {
    assertThat(packagesOf(new AnnotationTypeFilter(RestController.class)))
      .isNotEmpty()
      .allMatch(name -> name.matches("com\\.muscat\\.user\\.(api|domain\\.[a-z]+)\\.controller"));
  }

  @Test
  @DisplayName("예외와 예외 핸들러는 common.exception 에 있다")
  void exceptions() {
    assertThat(packagesOf(new AssignableTypeFilter(BaseException.class)))
      .containsExactly(BASE + ".common.exception");
    assertThat(packagesOf(new AnnotationTypeFilter(RestControllerAdvice.class)))
      .containsExactly(BASE + ".common.exception");
  }

  @Test
  @DisplayName("응답 코드는 common.enums.response 에 있다")
  void responseCodes() {
    assertThat(packagesOf(new AssignableTypeFilter(ErrorCode.class)))
      .containsExactly(BASE + ".common.enums.response");
  }

  // 본 코드 클래스만 보고 테스트 클래스는 뺌
  private static List<String> packagesOf(TypeFilter filter) {
    ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
      @Override
      protected boolean isCandidateComponent(AnnotatedBeanDefinition definition) {
        return true;
      }
    };
    scanner.addIncludeFilter(filter);
    return scanner.findCandidateComponents(BASE).stream()
      .filter(definition -> !definition.getResourceDescription().contains("test-classes"))
      .map(definition -> definition.getBeanClassName())
      .map(name -> name.substring(0, name.lastIndexOf('.')))
      .distinct()
      .toList();
  }
}
