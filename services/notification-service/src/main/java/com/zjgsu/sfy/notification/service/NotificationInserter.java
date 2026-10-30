package com.zjgsu.sfy.notification.service;

import com.zjgsu.sfy.notification.common.NotificationType;
import com.zjgsu.sfy.notification.entity.Notification;
import com.zjgsu.sfy.notification.repository.NotificationRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 通知写入的独立事务边界。
 *
 * <p>每次写入开启新事务，与事件发布方（已提交）或提醒任务事务隔离；{@code dedupe_key} 唯一键冲突会在提交时抛出， 由事务外的 {@link
 * NotificationWriter} 捕获并视为「已消费」。
 */
@Service
public class NotificationInserter {

  private final NotificationRepository notificationRepository;

  public NotificationInserter(NotificationRepository notificationRepository) {
    this.notificationRepository = notificationRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Notification insert(
      UUID recipientUserId,
      NotificationType type,
      UUID sourceId,
      String dedupeKey,
      String title,
      String body) {
    return notificationRepository.saveAndFlush(
        Notification.create(recipientUserId, type, sourceId, dedupeKey, title, body));
  }
}
