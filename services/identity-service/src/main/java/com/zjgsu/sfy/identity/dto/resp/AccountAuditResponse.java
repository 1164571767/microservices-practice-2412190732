package com.zjgsu.sfy.identity.dto.resp;

import com.zjgsu.sfy.identity.entity.AccountAudit;
import java.time.Instant;
import java.util.UUID;

public record AccountAuditResponse(
    UUID id,
    UUID actorId,
    String action,
    String reason,
    String beforeSummary,
    String afterSummary,
    Instant createdAt) {
  public static AccountAuditResponse from(AccountAudit audit) {
    return new AccountAuditResponse(
        audit.getId(),
        audit.getActorUserId(),
        audit.getAction(),
        audit.getReason(),
        audit.getBeforeSummary(),
        audit.getAfterSummary(),
        audit.getCreatedAt());
  }
}
