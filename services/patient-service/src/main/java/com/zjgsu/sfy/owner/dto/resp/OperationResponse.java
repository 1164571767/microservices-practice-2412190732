package com.zjgsu.sfy.owner.dto.resp;

import com.zjgsu.sfy.owner.entity.ArchiveOperation;
import java.time.Instant;
import java.util.UUID;

public record OperationResponse(
    UUID id,
    String action,
    UUID actorId,
    String actorName,
    String reason,
    String beforeJson,
    String afterJson,
    Instant createdAt) {
  public static OperationResponse from(ArchiveOperation o) {
    return new OperationResponse(
        o.getId(),
        o.getAction(),
        o.getActorId(),
        o.getActorName(),
        o.getReason(),
        o.getBeforeJson(),
        o.getAfterJson(),
        o.getCreatedAt());
  }
}
