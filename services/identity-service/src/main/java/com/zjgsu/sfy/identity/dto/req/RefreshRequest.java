package com.zjgsu.sfy.identity.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "刷新令牌 / 退出登录的请求体")
public record RefreshRequest(
    @Schema(
            description = "登录或刷新时下发的 refreshToken（不透明字符串，长度可达 512）",
            example = "8f2c4b1e9d7a6c5b3a2f1e0d9c8b7a6f5e4d3c2b1a0f9e8d7c6b5a4f3e2d1c0b")
        @NotBlank
        @Size(max = 512)
        String refreshToken) {}
