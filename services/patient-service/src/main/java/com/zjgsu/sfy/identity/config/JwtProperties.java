package com.zjgsu.sfy.identity.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** JWT 配置。secret 从环境变量注入，不落源码；HS256 要求至少 256 位（32 字节）密钥。 */
@ConfigurationProperties(prefix = "petcare.security.jwt")
public record JwtProperties(String secret, String issuer, Duration accessTtl, Duration refreshTtl) {

  private static final int MIN_SECRET_LENGTH = 32;
  private static final String DEFAULT_ISSUER = "https://petcare.local";
  private static final Duration DEFAULT_ACCESS_TTL = Duration.ofMinutes(15);
  private static final Duration DEFAULT_REFRESH_TTL = Duration.ofDays(7);

  public JwtProperties {
    if (secret == null || secret.length() < MIN_SECRET_LENGTH) {
      throw new IllegalStateException(
          "petcare.security.jwt.secret must be set to at least "
              + MIN_SECRET_LENGTH
              + " characters");
    }
    issuer = (issuer == null || issuer.isBlank()) ? DEFAULT_ISSUER : issuer;
    accessTtl = accessTtl == null ? DEFAULT_ACCESS_TTL : accessTtl;
    refreshTtl = refreshTtl == null ? DEFAULT_REFRESH_TTL : refreshTtl;
  }

  public SecretKey secretKey() {
    return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
  }
}
