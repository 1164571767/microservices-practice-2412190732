package com.zjgsu.sfy.identity.dto.resp;

import com.zjgsu.sfy.identity.service.result.TokenPair;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "一对访问令牌")
public record TokenResponse(
    @Schema(
            description = "访问令牌，调用其他接口时放在请求头 Authorization: Bearer <accessToken>",
            example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI2ZjFkMmMzYiJ9.5vQ2m0r8kQ")
        String accessToken,
    @Schema(
            description = "刷新令牌，仅用于 /api/auth/refresh 换新令牌和 /api/auth/logout 退出",
            example = "8f2c4b1e9d7a6c5b3a2f1e0d9c8b7a6f5e4d3c2b1a0f9e8d7c6b5a4f3e2d1c0b")
        String refreshToken,
    @Schema(description = "令牌类型，固定为 Bearer", example = "Bearer") String tokenType,
    @Schema(description = "accessToken 有效期，单位秒", example = "900") long expiresIn) {

  public static TokenResponse from(TokenPair pair) {
    return new TokenResponse(
        pair.accessToken(), pair.refreshToken(), pair.tokenType(), pair.expiresIn());
  }
}
