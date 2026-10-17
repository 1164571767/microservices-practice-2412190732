package com.zjgsu.sfy.identity.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class JwtPropertiesTest {

  @Test
  void rejectsSecretShorterThan256Bits() {
    assertThatThrownBy(() -> new JwtProperties("too-short", null, null, null))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectsMissingSecret() {
    assertThatThrownBy(() -> new JwtProperties(null, null, null, null))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void appliesSensibleDefaults() {
    JwtProperties properties =
        new JwtProperties("0123456789abcdef0123456789abcdef", null, null, null);

    assertThat(properties.issuer()).isEqualTo("https://petcare.local");
    assertThat(properties.accessTtl()).isEqualTo(Duration.ofMinutes(15));
    assertThat(properties.refreshTtl()).isEqualTo(Duration.ofDays(7));
  }
}
