package com.zjgsu.sfy.common.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 幂等请求指纹：同键请求必须得到同一指纹，否则判为 {@code IDEMPOTENCY_CONFLICT}。 */
public final class RequestHashes {

  private RequestHashes() {}

  public static String sha256(String canonical) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 unavailable", ex);
    }
  }
}
