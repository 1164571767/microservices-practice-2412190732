package com.zjgsu.sfy.identity.dto.resp;

import com.zjgsu.sfy.identity.service.result.TokenPair;

public record TokenResponse(
    String accessToken, String refreshToken, String tokenType, long expiresIn) {

  public static TokenResponse from(TokenPair pair) {
    return new TokenResponse(
        pair.accessToken(), pair.refreshToken(), pair.tokenType(), pair.expiresIn());
  }
}
