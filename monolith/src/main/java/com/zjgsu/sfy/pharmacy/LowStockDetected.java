package com.zjgsu.sfy.pharmacy;

import java.time.Instant;
import java.util.UUID;

/**
 * 低库存事件：某药品库存由「不低于阈值」变为「低于阈值」时发布一次。
 *
 * <p>由 pharmacy 发布，供通知 / 报表等下游模块订阅；同一段低库存期间不重复发布，补货回到阈值以上后再次触底会重新发布。
 */
public record LowStockDetected(
    UUID drugId,
    String drugName,
    String specification,
    int quantity,
    int threshold,
    Instant detectedAt) {}
