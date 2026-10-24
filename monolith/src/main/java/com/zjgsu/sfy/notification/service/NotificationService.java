package com.zjgsu.sfy.notification.service;

import com.zjgsu.sfy.common.error.BusinessException;
import com.zjgsu.sfy.common.error.ErrorCode;
import com.zjgsu.sfy.identity.CurrentUser;
import com.zjgsu.sfy.notification.common.NotificationStatus;
import com.zjgsu.sfy.notification.entity.Notification;
import com.zjgsu.sfy.notification.repository.NotificationRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 站内通知查询与已读用例；所有读取 / 变更都限定为当前用户自己的通知。 */
@Service
public class NotificationService {

  private final NotificationRepository notificationRepository;

  public NotificationService(NotificationRepository notificationRepository) {
    this.notificationRepository = notificationRepository;
  }

  @Transactional(readOnly = true)
  public Page<Notification> list(
      CurrentUser requester, NotificationStatus status, Pageable pageable) {
    return status == null
        ? notificationRepository.findByRecipientUserId(requester.userId(), pageable)
        : notificationRepository.findByRecipientUserIdAndStatus(
            requester.userId(), status, pageable);
  }

  @Transactional(readOnly = true)
  public Notification get(CurrentUser requester, UUID notificationId) {
    return requireOwned(requester, notificationId);
  }

  /** 标记已读；重复标记幂等，返回当前状态。 */
  @Transactional
  public Notification markRead(CurrentUser requester, UUID notificationId) {
    Notification notification = requireOwned(requester, notificationId);
    if (notification.markRead()) {
      notificationRepository.saveAndFlush(notification);
    }
    return notification;
  }

  @Transactional
  public int markAllRead(CurrentUser requester) {
    return notificationRepository.markAllRead(
        requester.userId(), NotificationStatus.READ, NotificationStatus.UNREAD, Instant.now());
  }

  @Transactional(readOnly = true)
  public long unreadCount(CurrentUser requester) {
    return notificationRepository.countByRecipientUserIdAndStatus(
        requester.userId(), NotificationStatus.UNREAD);
  }

  private Notification requireOwned(CurrentUser requester, UUID notificationId) {
    Notification notification =
        notificationRepository
            .findById(notificationId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "通知不存在"));
    if (!notification.getRecipientUserId().equals(requester.userId())) {
      throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
    }
    return notification;
  }
}
