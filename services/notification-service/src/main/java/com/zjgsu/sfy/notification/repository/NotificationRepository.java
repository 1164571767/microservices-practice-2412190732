package com.zjgsu.sfy.notification.repository;

import com.zjgsu.sfy.notification.common.NotificationStatus;
import com.zjgsu.sfy.notification.entity.Notification;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

  Page<Notification> findByRecipientUserId(UUID recipientUserId, Pageable pageable);

  Page<Notification> findByRecipientUserIdAndStatus(
      UUID recipientUserId, NotificationStatus status, Pageable pageable);

  long countByRecipientUserIdAndStatus(UUID recipientUserId, NotificationStatus status);

  /** 一键已读：只更新本人未读通知，重复执行影响行数为 0。 */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update Notification n set n.status = :read, n.readAt = :readAt, n.updatedAt = :readAt "
          + "where n.recipientUserId = :recipientUserId and n.status = :unread")
  int markAllRead(
      @Param("recipientUserId") UUID recipientUserId,
      @Param("read") NotificationStatus read,
      @Param("unread") NotificationStatus unread,
      @Param("readAt") Instant readAt);
}
