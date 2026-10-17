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

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(nullable = false, length = 20)
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

  protected Owner() {}

  public static Owner create(UUID userId, String name, String phone, String address) {
    Owner owner = new Owner();
    owner.userId = Objects.requireNonNull(userId, "userId must not be null");
    owner.applyDetails(name, phone, address);
    return owner;
  }

  public void updateDetails(String name, String phone, String address) {
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
    return userId.equals(candidateUserId);
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
