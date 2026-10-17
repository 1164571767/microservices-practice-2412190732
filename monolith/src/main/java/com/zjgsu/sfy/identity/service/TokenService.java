package com.zjgsu.sfy.identity.service;

import com.zjgsu.sfy.identity.entity.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/**
 * 签发短期 access token（JWT，HS256）并生成高熵 refresh token。
 *
 * <p>access token 只承载身份声明（用户 id、用户名、角色）；权限在服务端校验。 refresh token 为不透明随机串，仅存哈希， 由服务端状态控制轮换与撤销。
 */
public class TokenService {

  private static final String ACCESS_TOKEN_TYPE = "access";
  private static final int REFRESH_TOKEN_BYTES = 32;

  private final JwtEncoder jwtEncoder;
  private final String issuer;
  private final Duration accessTtl;
  private final Duration refreshTtl;
  private final SecureRandom secureRandom = new SecureRandom();

  public TokenService(
      JwtEncoder jwtEncoder, String issuer, Duration accessTtl, Duration refreshTtl) {
    this.jwtEncoder = jwtEncoder;
    this.issuer = issuer;
    this.accessTtl = accessTtl;
    this.refreshTtl = refreshTtl;
  }

  public String issueAccessToken(User user) {
    Instant now = Instant.now();
    List<String> roles = user.getRoles().stream().map(Enum::name).sorted().toList();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .issuedAt(now)
            .expiresAt(now.plus(accessTtl))
            .subject(user.getId().toString())
            .claim("username", user.getUsername())
            .claim("roles", roles)
            .claim("typ", ACCESS_TOKEN_TYPE)
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  public String newRefreshToken() {
    byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /** refresh token 为高熵随机值，SHA-256 足以用于不可逆存储。 */
  public String hash(String rawToken) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 not available", ex);
    }
  }

  public Duration accessTtl() {
    return accessTtl;
  }

  public Duration refreshTtl() {
    return refreshTtl;
  }
}
