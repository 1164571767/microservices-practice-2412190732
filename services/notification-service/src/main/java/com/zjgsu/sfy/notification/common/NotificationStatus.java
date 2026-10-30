package com.zjgsu.sfy.notification.common;

import io.swagger.v3.oas.annotations.media.Schema;

/** 通知阅读状态：站内通知只分未读 / 已读，状态只能经实体领域方法流转。 */
@Schema(description = "通知阅读状态：UNREAD 未读 / READ 已读")
public enum NotificationStatus {
  UNREAD,
  READ
}
