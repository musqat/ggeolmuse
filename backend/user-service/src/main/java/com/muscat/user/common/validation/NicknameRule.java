package com.muscat.user.common.validation;

import java.util.regex.Pattern;

// 닉네임 규칙(2 ~ 20자, 영문 · 숫자 · 한글 · _)
public final class NicknameRule {

  public static final int MIN_LENGTH = 2;
  public static final int MAX_LENGTH = 20;
  public static final String PATTERN = "^[a-zA-Z0-9가-힣_]+$";
  public static final String MESSAGE = "닉네임은 2 ~ 20자의 영문, 숫자, 한글, _ 만 쓸 수 있습니다";

  private static final Pattern NOT_ALLOWED = Pattern.compile("[^a-zA-Z0-9가-힣_]");

  private NicknameRule() {
  }

  // 규칙 밖 글자를 빼고 최대 길이로 자른 값(남은 글자가 최소 길이보다 짧으면 null)
  public static String clean(String name) {
    if (name == null) {
      return null;
    }
    String cleaned = NOT_ALLOWED.matcher(name).replaceAll("");
    if (cleaned.length() > MAX_LENGTH) {
      cleaned = cleaned.substring(0, MAX_LENGTH);
    }
    return cleaned.length() < MIN_LENGTH ? null : cleaned;
  }
}
