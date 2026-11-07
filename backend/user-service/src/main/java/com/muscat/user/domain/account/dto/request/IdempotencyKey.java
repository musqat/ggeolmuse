package com.muscat.user.domain.account.dto.request;

import com.muscat.user.common.enums.response.AccountResponse;
import com.muscat.user.common.exception.AccountException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

// 요청 헤더로 받은 중복 요청 키(하루 안에 같은 사용자 · 같은 키로 온 입금 · 환전은 한 번만 실행)
public record IdempotencyKey(String value) {

  public static final String HEADER = "Idempotency-Key";

  // UUID 를 담는 1 ~ 64자 영문 · 숫자 · 하이픈
  private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9-]{1,64}");

  // 헤더 값의 키(헤더가 없으면 null, 비었거나 모양이 틀리면 400)
  public static IdempotencyKey parse(String header) {
    if (header == null) {
      return null;
    }
    if (!FORMAT.matcher(header).matches()) {
      throw new AccountException(AccountResponse.INVALID_IDEMPOTENCY_KEY);
    }
    return new IdempotencyKey(header);
  }

  // 작업 이름과 요청 값의 SHA-256(소수 끝 0 은 무시)
  public static String fingerprint(String operation, Object... values) {
    StringBuilder text = new StringBuilder(operation);
    for (Object value : values) {
      text.append('|').append(value instanceof BigDecimal number
        ? number.stripTrailingZeros().toPlainString()
        : String.valueOf(value));
    }
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
