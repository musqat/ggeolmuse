package com.muscat.commonlib.util;

import com.muscat.commonlib.exception.BusinessException;
import com.muscat.commonlib.exception.CommonResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 문자열의 SHA-256 해시
 */
public final class Hashes {

  private Hashes() {
  }

  // UTF-8 바이트의 SHA-256 소문자 16진수 64자
  public static String sha256Hex(String text) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new BusinessException(CommonResponse.HASH_UNAVAILABLE, e);
    }
  }
}
