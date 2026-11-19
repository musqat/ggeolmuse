package com.muscat.commonlib.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SHA-256 해시")
class HashesTest {

  @Test
  @DisplayName("UTF-8 바이트의 SHA-256 을 소문자 16진수 64자로 낸다")
  void sha256Hex() {
    assertThat(Hashes.sha256Hex("abc")).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    assertThat(Hashes.sha256Hex("가")).hasSize(64).isEqualTo(Hashes.sha256Hex("가"));
  }
}
