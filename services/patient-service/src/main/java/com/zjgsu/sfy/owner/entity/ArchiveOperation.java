package com.zjgsu.sfy.owner.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** Append-only operation snapshots; no cross-module entity references. */
@Entity
@Table(name = "owner_operation")
public class ArchiveOperation {
  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "target_id", nullable = false)
  private UUID targetId;

  @Column(nullable = false, length = 30)
  private String action;

  @Column(name = "actor_id", nullable = false)
  private UUID actorId;

  @Column(name = "actor_name", nullable = false, length = 100)
  private String actorName;

  @Column(length = 255)
  private String reason;

  @Column(name = "before_json", columnDefinition = "longtext")
  private String beforeJson;

  @Column(name = "after_json", nullable = false, columnDefinition = "longtext")
  private String afterJson;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected ArchiveOperation() {}

  public static ArchiveOperation record(
      UUID target,
      String action,
      UUID actor,
      String name,
      String reason,
      String before,
      String after) {
    ArchiveOperation o = new ArchiveOperation();
    o.targetId = target;
    o.action = action;
    o.actorId = actor;
    o.actorName = name;
    o.reason = reason;
    o.beforeJson = before;
    o.afterJson = after;
    o.createdAt = Instant.now();
    return o;
  }

  public UUID getId() {
    return id;
  }

  public String getAction() {
    return action;
  }

  public UUID getActorId() {
    return actorId;
  }

  public String getActorName() {
    return actorName;
  }

  public String getReason() {
    return reason;
  }

  public String getBeforeJson() {
    return beforeJson;
  }

  public String getAfterJson() {
    return afterJson;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
