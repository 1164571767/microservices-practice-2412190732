package com.zjgsu.sfy.identity.entity;

import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.common.UserStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "users")
public class User {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(nullable = false, length = 50)
  private String username;

  @Column(nullable = false, length = 20)
  private String phone;

  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

  @Column(name = "display_name", nullable = false, length = 100)
  private String displayName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private UserStatus status;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "user_role", joinColumns = @JoinColumn(name = "user_id"))
  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, length = 20)
  private Set<Role> roles = new HashSet<>();

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private long version;

  @Column(name = "session_version", nullable = false)
  private long sessionVersion;

  protected User() {}

  public static User create(
      String username, String phone, String passwordHash, String displayName, Set<Role> roles) {
    if (roles == null || roles.isEmpty()) {
      throw new IllegalArgumentException("user must have at least one role");
    }
    User user = new User();
    user.username = normalizeUsername(username);
    user.phone = phone.trim();
    user.passwordHash = passwordHash;
    user.displayName = displayName.trim();
    user.status = UserStatus.ACTIVE;
    user.roles = new HashSet<>(roles);
    return user;
  }

  /** 用户名大小写不敏感，统一以小写形式存储与查询。 */
  public static String normalizeUsername(String username) {
    return username.trim().toLowerCase(Locale.ROOT);
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

  public boolean isActive() {
    return status == UserStatus.ACTIVE;
  }

  public UUID getId() {
    return id;
  }

  public String getUsername() {
    return username;
  }

  public String getPhone() {
    return phone;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getDisplayName() {
    return displayName;
  }

  public UserStatus getStatus() {
    return status;
  }

  public Set<Role> getRoles() {
    return Set.copyOf(roles);
  }

  public long getVersion() {
    return version;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public long getSessionVersion() {
    return sessionVersion;
  }

  public void changePassword(String encodedPassword) {
    passwordHash = encodedPassword;
    sessionVersion++;
  }

  public void updateEmployee(String name, String phone, Set<Role> roles, UserStatus status) {
    if (!this.roles.equals(roles) || this.status != status) sessionVersion++;
    this.displayName = name.trim();
    this.phone = phone.trim();
    this.roles = new HashSet<>(roles);
    this.status = status;
  }
}
