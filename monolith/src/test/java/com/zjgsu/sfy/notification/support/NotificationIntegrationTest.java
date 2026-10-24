package com.zjgsu.sfy.notification.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.zjgsu.sfy.billing.support.BillingIntegrationTest;
import com.zjgsu.sfy.identity.entity.User;
import com.zjgsu.sfy.notification.common.NotificationStatus;
import com.zjgsu.sfy.notification.common.NotificationType;
import com.zjgsu.sfy.notification.entity.Notification;
import com.zjgsu.sfy.notification.repository.NotificationRepository;
import com.zjgsu.sfy.notification.service.RevisitReminderService;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

/** notification 模块集成测试基类：复用 identity / owner / appointment / medical / billing 夹具与真实 MySQL。 */
public abstract class NotificationIntegrationTest extends BillingIntegrationTest {

  @Autowired protected NotificationRepository notificationRepository;
  @Autowired protected ApplicationEventPublisher eventPublisher;
  @Autowired protected RevisitReminderService revisitReminderService;

  protected List<Notification> notificationsOf(UUID recipientUserId) {
    return notificationRepository.findAll().stream()
        .filter(notification -> notification.getRecipientUserId().equals(recipientUserId))
        .sorted(Comparator.comparing(Notification::getCreatedAt).thenComparing(Notification::getId))
        .toList();
  }

  protected List<Notification> notificationsOf(UUID recipientUserId, NotificationType type) {
    return notificationsOf(recipientUserId).stream()
        .filter(notification -> notification.getType() == type)
        .toList();
  }

  protected Notification onlyNotificationOf(UUID recipientUserId, NotificationType type) {
    List<Notification> matches = notificationsOf(recipientUserId, type);
    assertThat(matches).hasSize(1);
    return matches.getFirst();
  }

  protected long unreadCountOf(UUID recipientUserId) {
    return notificationRepository.countByRecipientUserIdAndStatus(
        recipientUserId, NotificationStatus.UNREAD);
  }

  protected Notification seedNotification(
      User recipient, NotificationType type, UUID sourceId, String title) {
    return notificationRepository.saveAndFlush(
        Notification.create(
            recipient.getId(),
            type,
            sourceId,
            type + ":" + sourceId + ":" + recipient.getId(),
            title,
            "通知正文"));
  }
}
