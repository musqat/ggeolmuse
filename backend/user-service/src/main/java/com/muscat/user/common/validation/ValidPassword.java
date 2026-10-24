package com.muscat.user.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// 새 비밀번호 규칙(8 ~ 100자, 영문 · 숫자 · 특수문자 하나 이상)
@NotBlank
@Size(min = 8, max = 100)
@Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$")
@ReportAsSingleViolation
@Constraint(validatedBy = {})
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ValidPassword {

  String message() default "비밀번호는 8 ~ 100자이고 영문, 숫자, 특수문자를 각각 하나 이상 포함해야 합니다";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
