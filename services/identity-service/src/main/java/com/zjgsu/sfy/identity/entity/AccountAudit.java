package com.zjgsu.sfy.identity.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "identity_account_audit")
public class AccountAudit {
  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "target_user_id", nullable = false)
  private UUID targetUserId;

  @Column(name = "actor_user_id", nullable = false)
  private UUID actorUserId;

  @Column(nullable = false, length = 40)
  private String action;

  @Column(nullable = false, length = 500)
  private String reason;

  @Column(name = "before_summary", length = 1000)
  private String beforeSummary;

  @Column(name = "after_summary", length = 1000)
  private String afterSummary;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected AccountAudit() {}

  public AccountAudit(
      UUID target, UUID actor, String action, String reason, String before, String after) {
    targetUserId = target;
    actorUserId = actor;
    this.action = action;
    this.reason = reason;
    beforeSummary = before;
    afterSummary = after;
    createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getActorUserId() {
    return actorUserId;
  }

  public String getAction() {
    return action;
  }

  public String getReason() {
    return reason;
  }

  public String getBeforeSummary() {
    return beforeSummary;
  }

  public String getAfterSummary() {
    return afterSummary;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
