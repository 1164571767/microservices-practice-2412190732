package com.zjgsu.sfy.identity.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import com.zjgsu.sfy.identity.repository.UserRepository;
import com.zjgsu.sfy.identity.service.TokenService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, BootstrapAdminProperties.class})
public class IdentitySecurityConfig {

  @Bean
  public PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  @Bean
  public JwtEncoder jwtEncoder(JwtProperties properties) {
    ImmutableSecret<SecurityContext> secret = new ImmutableSecret<>(properties.secretKey());
    return new NimbusJwtEncoder(secret);
  }

  @Bean
  public JwtDecoder jwtDecoder(JwtProperties properties) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(properties.secretKey())
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(properties.issuer()), accessTokenOnly()));
    return decoder;
  }

  @Bean
  public TokenService tokenService(JwtEncoder jwtEncoder, JwtProperties properties) {
    return new TokenService(
        jwtEncoder, properties.issuer(), properties.accessTtl(), properties.refreshTtl());
  }

  @Bean
  public AdminBootstrap adminBootstrap(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      BootstrapAdminProperties properties) {
    return new AdminBootstrap(userRepository, passwordEncoder, properties);
  }

  /** refresh token 是不透明随机串而非 JWT，因此 access token 通道必须拒绝非 access 类型。 */
  private static OAuth2TokenValidator<Jwt> accessTokenOnly() {
    return token ->
        "access".equals(token.getClaimAsString("typ"))
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Not an access token", null));
  }
}
