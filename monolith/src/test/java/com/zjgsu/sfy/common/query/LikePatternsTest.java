package com.zjgsu.sfy.common.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zjgsu.sfy.common.error.BusinessException;
import com.zjgsu.sfy.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

class LikePatternsTest {

  @Test
  void blankQueryMatchesEverything() {
    assertThat(LikePatterns.contains(null)).isEqualTo("%");
    assertThat(LikePatterns.contains("  ")).isEqualTo("%");
  }

  @Test
  void wrapsNormalQueryAndLowercases() {
    assertThat(LikePatterns.contains("  Amoxi  ")).isEqualTo("%amoxi%");
  }

  @Test
  void escapesLikeMetacharactersSoUsersCannotWidenTheMatch() {
    assertThat(LikePatterns.contains("100%_off!")).isEqualTo("%100!%!_off!!%");
  }

  @Test
  void rejectsOverlongQuery() {
    String tooLong = "a".repeat(LikePatterns.MAX_QUERY_LENGTH + 1);

    assertThatThrownBy(() -> LikePatterns.contains(tooLong))
        .isInstanceOf(BusinessException.class)
        .extracting(error -> ((BusinessException) error).errorCode())
        .isEqualTo(ErrorCode.VALIDATION_FAILED);
  }
}
