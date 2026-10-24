package com.zjgsu.sfy.owner.common;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "过敏严重程度：MILD 轻微 / MODERATE 中度 / SEVERE 严重")
public enum AllergySeverity {
  MILD,
  MODERATE,
  SEVERE
}
