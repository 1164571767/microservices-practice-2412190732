package com.zjgsu.sfy.identity.service.result;

public record TokenPair(
    String accessToken, String refreshToken, String tokenType, long expiresIn) {}
