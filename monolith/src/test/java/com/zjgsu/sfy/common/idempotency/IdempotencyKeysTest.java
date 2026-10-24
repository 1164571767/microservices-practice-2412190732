package com.zjgsu.sfy.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zjgsu.sfy.common.error.BusinessException;
import com.zjgsu.sfy.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

class IdempotencyKeysTest {

  @Test
  void blankKeyMeansNoIdempotency() {
    assertThat(IdempotencyKeys.normalize(null)).isNull();
    assertThat(IdempotencyKeys.normalize("   ")).isNull();
  }

  @Test
  void trimsAndAcceptsAllowedCharacters() {
    assertThat(IdempotencyKeys.normalize("  order-123:pay_1.v2  ")).isEqualTo("order-123:pay_1.v2");
  }

  @Test
  void rejectsOverlongKeyBeforeItReachesTheDatabase() {
    String tooLong = "a".repeat(IdempotencyKeys.MAX_LENGTH + 1);

    assertThatThrownBy(() -> IdempotencyKeys.normalize(tooLong))
        .isInstanceOf(BusinessException.class)
        .extracting(error -> ((BusinessException) error).errorCode())
        .isEqualTo(ErrorCode.VALIDATION_FAILED);
  }

  @Test
  void rejectsCharactersOutsideWhitelist() {
    assertThatThrownBy(() -> IdempotencyKeys.normalize("key with space"))
        .isInstanceOf(BusinessException.class)
        .extracting(error -> ((BusinessException) error).errorCode())
        .isEqualTo(ErrorCode.VALIDATION_FAILED);
  }
}
