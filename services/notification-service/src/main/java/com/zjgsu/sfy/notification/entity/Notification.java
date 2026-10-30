package com.zjgsu.sfy.notification.entity;

import com.zjgsu.sfy.notification.common.NotificationStatus;
import com.zjgsu.sfy.notification.common.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * 站内通知聚合根。
 *
 * <p>{@code dedupe_key} 唯一键是消费幂等的最终守卫：同一事件（或同一提醒来源）对同一接收人只允许一条通知。 已读状态只能经 {@link #markRead()}
 * 流转，数据库另有「状态与已读时间一致」CHECK 兜底。
 */
@Entity
@Table(name = "notification")
public class Notification {

  private static final int MAX_TITLE_LENGTH = 120;
  private static final int MAX_BODY_LENGTH = 1000;
  private static final int MAX_DEDUPE_KEY_LENGTH = 200;

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "recipient_user_id", nullable = false)
  private UUID recipientUserId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private NotificationType type;

  @Column(nullable = false, length = MAX_TITLE_LENGTH)
  private String title;

  @Column(nullable = false, length = MAX_BODY_LENGTH)
  private String body;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private NotificationStatus status;

  @Column(name = "source_id", nullable = false)
  private UUID sourceId;

  @Column(name = "dedupe_key", nullable = false, length = MAX_DEDUPE_KEY_LENGTH)
  private String dedupeKey;

  @Column(name = "read_at")
  private Instant readAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private long version;

  protected Notification() {}

  public static Notification create(
      UUID recipientUserId,
      NotificationType type,
      UUID sourceId,
      String dedupeKey,
      String title,
      String body) {
    Notification notification = new Notification();
    notification.recipientUserId =
        Objects.requireNonNull(recipientUserId, "recipientUserId must not be null");
    notification.type = Objects.requireNonNull(type, "type must not be null");
    notification.sourceId = Objects.requireNonNull(sourceId, "sourceId must not be null");
    notification.dedupeKey = requireText(dedupeKey, MAX_DEDUPE_KEY_LENGTH, "dedupeKey");
    notification.title = requireText(title, MAX_TITLE_LENGTH, "title");
    notification.body = requireText(body, MAX_BODY_LENGTH, "body");
    notification.status = NotificationStatus.UNREAD;
    return notification;
  }

  private static String requireText(String value, int maxLength, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    String trimmed = value.trim();
    if (trimmed.length() > maxLength) {
      throw new IllegalArgumentException(field + " must be at most " + maxLength + " characters");
    }
    return trimmed;
  }

  public boolean isUnread() {
    return status == NotificationStatus.UNREAD;
  }

  /** 标记已读；已读时幂等返回 {@code false}（状态不变）。时间截断到微秒，与数据库 {@code DATETIME(6)} 精度一致。 */
  public boolean markRead() {
    if (status == NotificationStatus.READ) {
      return false;
    }
    this.status = NotificationStatus.READ;
    this.readAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    return true;
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

  public UUID getId() {
    return id;
  }

  public UUID getRecipientUserId() {
    return recipientUserId;
  }

  public NotificationType getType() {
    return type;
  }

  public String getTitle() {
    return title;
  }

  public String getBody() {
    return body;
  }

  public NotificationStatus getStatus() {
    return status;
  }

  public UUID getSourceId() {
    return sourceId;
  }

  public String getDedupeKey() {
    return dedupeKey;
  }

  public Instant getReadAt() {
    return readAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
