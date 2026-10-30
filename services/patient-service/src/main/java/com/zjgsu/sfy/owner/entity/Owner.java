package com.zjgsu.sfy.owner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** 主人档案；与 identity 用户一一对应，但不持有对 users 表的数据库外键（数据所有权独立）。 */
@Entity
@Table(name = "owner")
public class Owner {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "user_id")
  private UUID userId;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(nullable = false, length = 21)
  private String phone;

  @Column(length = 255)
  private String address;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private long version;

  @Column(name = "archived_at")
  private Instant archivedAt;

  @Column(name = "archived_by")
  private UUID archivedBy;

  @Column(name = "archive_reason", length = 255)
  private String archiveReason;

  public boolean isArchived() {
    return archivedAt != null;
  }

  public Instant getArchivedAt() {
    return archivedAt;
  }

  public UUID getArchivedBy() {
    return archivedBy;
  }

  public String getArchiveReason() {
    return archiveReason;
  }

  public long getVersion() {
    return version;
  }

  public void requireActive() {
    if (isArchived())
      throw new com.zjgsu.sfy.common.error.BusinessException(
          com.zjgsu.sfy.common.error.ErrorCode.OPERATION_NOT_ALLOWED, "档案已归档，仅可查阅历史");
  }

  public void archive(UUID actor, String reason) {
    requireActive();
    if (reason == null || reason.isBlank() || reason.trim().length() > 255)
      throw new IllegalArgumentException("archive reason required");
    archivedAt = Instant.now();
    archivedBy = Objects.requireNonNull(actor);
    archiveReason = reason.trim();
  }

  protected Owner() {}

  public static Owner create(UUID userId, String name, String phone, String address) {
    Owner owner = new Owner();
    owner.userId = Objects.requireNonNull(userId, "userId must not be null");
    owner.applyDetails(name, phone, address);
    return owner;
  }

  public static Owner createUnbound(String name, String phone, String address) {
    Owner owner = new Owner();
    owner.applyDetails(name, phone, address);
    return owner;
  }

  public void bind(UUID userId) {
    requireActive();
    if (this.userId != null)
      throw new com.zjgsu.sfy.common.error.BusinessException(
          com.zjgsu.sfy.common.error.ErrorCode.OPERATION_NOT_ALLOWED, "档案已绑定账号，禁止覆盖绑定");
    this.userId = Objects.requireNonNull(userId);
  }

  public void updateDetails(String name, String phone, String address) {
    requireActive();
    applyDetails(name, phone, address);
  }

  private void applyDetails(String name, String phone, String address) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    if (phone == null || phone.isBlank()) {
      throw new IllegalArgumentException("phone must not be blank");
    }
    this.name = name.trim();
    this.phone = phone.trim();
    this.address = address == null || address.isBlank() ? null : address.trim();
  }

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    this.updatedAt = Instant.now();
  }

  public boolean belongsTo(UUID candidateUserId) {
    return userId != null && userId.equals(candidateUserId);
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getName() {
    return name;
  }

  public String getPhone() {
    return phone;
  }

  public String getAddress() {
    return address;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
