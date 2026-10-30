package com.zjgsu.sfy.integration;

import com.zjgsu.sfy.identity.config.JwtProperties;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class RemoteJwtConfig {
  @Bean
  JwtDecoder jwtDecoder(JwtProperties properties, HttpTransport http, PeerProperties peers) {
    var decoder =
        NimbusJwtDecoder.withSecretKey(properties.secretKey())
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
    var basic = JwtValidators.createDefaultWithIssuer(properties.issuer());
    // Validate signature/expiry/type before any identity lookup. Never cache revocation checks.
    decoder.setJwtValidator(
        token -> {
          var result = basic.validate(token);
          if (result.hasErrors()) return result;
          if (!"access".equals(token.getClaimAsString("typ"))
              || !(token.getClaim("sv") instanceof Number)) return invalid();
          UUID id;
          try {
            id = UUID.fromString(token.getSubject());
          } catch (IllegalArgumentException ex) {
            return invalid();
          }
          Number version = token.getClaim("sv");
          Boolean active =
              http.required(
                  http.post(
                      peers.getIdentityUrl() + "/internal/identity/sessions/validate",
                      new SessionCheck(id, version.longValue()),
                      new ParameterizedTypeReference<Boolean>() {}));
          return active ? OAuth2TokenValidatorResult.success() : invalid();
        });
    return decoder;
  }

  private static OAuth2TokenValidatorResult invalid() {
    return OAuth2TokenValidatorResult.failure(
        new OAuth2Error("invalid_token", "Session revoked", null));
  }
}
