package com.zjgsu.sfy.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.entity.User;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class TokenServiceTest {

  private static final String SECRET = "unit-test-secret-must-be-32-chars-long";
  private static final String ISSUER = "https://petcare.test";

  private TokenService tokenService;
  private JwtDecoder jwtDecoder;

  @BeforeEach
  void setUp() {
    SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    this.tokenService =
        new TokenService(
            new NimbusJwtEncoder(new ImmutableSecret<>(key)),
            ISSUER,
            Duration.ofMinutes(15),
            Duration.ofDays(7));
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    // 与生产一致：校验签名、时间与 issuer（issuer 必须是合法 URL）。
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
    this.jwtDecoder = decoder;
  }

  @Test
  void accessTokenCarriesIdentityClaims() {
    User user =
        User.create(
            "alice", "13800000000", "{bcrypt}hash", "Alice", Set.of(Role.OWNER, Role.STAFF));
    UUID id = UUID.randomUUID();
    ReflectionTestUtils.setField(user, "id", id);

    Jwt jwt = jwtDecoder.decode(tokenService.issueAccessToken(user));

    assertThat(jwt.getSubject()).isEqualTo(id.toString());
    assertThat(jwt.getIssuer().toString()).isEqualTo(ISSUER);
    assertThat(jwt.getClaimAsString("username")).isEqualTo("alice");
    assertThat(jwt.getClaimAsStringList("roles")).containsExactly("OWNER", "STAFF");
    assertThat(jwt.getClaimAsString("typ")).isEqualTo("access");
    assertThat(jwt.getExpiresAt()).isNotNull();
  }

  @Test
  void refreshTokensAreUniqueAndHighEntropy() {
    String first = tokenService.newRefreshToken();
    String second = tokenService.newRefreshToken();

    assertThat(first).isNotEqualTo(second).hasSizeGreaterThanOrEqualTo(40);
  }

  @Test
  void hashIsDeterministicAndHidesRawValue() {
    String raw = tokenService.newRefreshToken();

    String hash = tokenService.hash(raw);

    assertThat(hash).hasSize(64).isEqualTo(tokenService.hash(raw)).doesNotContain(raw);
  }
}
