package com.zjgsu.sfy.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zjgsu.sfy.notification.common.NotificationStatus;
import com.zjgsu.sfy.notification.common.NotificationType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationTest {

  private static final UUID RECIPIENT = UUID.fromString("0192f3a0-0000-7000-8000-000000000001");
  private static final UUID SOURCE = UUID.fromString("0192f3a0-0000-7000-8000-000000000002");

  @Test
  void createStartsUnreadAndTrimsText() {
    Notification notification =
        Notification.create(
            RECIPIENT,
            NotificationType.BILL_PAID,
            SOURCE,
            " BILL_PAID:" + SOURCE + ":" + RECIPIENT,
            " 收费成功 ",
            " 已支付 50.00 CNY ");

    assertThat(notification.getStatus()).isEqualTo(NotificationStatus.UNREAD);
    assertThat(notification.getTitle()).isEqualTo("收费成功");
    assertThat(notification.getBody()).isEqualTo("已支付 50.00 CNY");
    assertThat(notification.getReadAt()).isNull();
    assertThat(notification.isUnread()).isTrue();
  }

  @Test
  void markReadIsIdempotent() {
    Notification notification =
        Notification.create(
            RECIPIENT, NotificationType.APPOINTMENT_BOOKED, SOURCE, "k1", "标题", "正文");

    assertThat(notification.markRead()).isTrue();
    assertThat(notification.getStatus()).isEqualTo(NotificationStatus.READ);
    assertThat(notification.getReadAt()).isNotNull();

    assertThat(notification.markRead()).isFalse();
    assertThat(notification.getStatus()).isEqualTo(NotificationStatus.READ);
  }

  @Test
  void createRejectsBlankFields() {
    assertThatThrownBy(
            () ->
                Notification.create(
                    RECIPIENT, NotificationType.QUEUE_CALLED, SOURCE, " ", "标题", "正文"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                Notification.create(
                    RECIPIENT, NotificationType.QUEUE_CALLED, SOURCE, "k2", " ", "正文"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                Notification.create(
                    RECIPIENT, NotificationType.QUEUE_CALLED, SOURCE, "k3", "标题", " "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsOversizedText() {
    String tooLongTitle = "标".repeat(121);
    assertThatThrownBy(
            () ->
                Notification.create(
                    RECIPIENT, NotificationType.REVISIT_REMINDER, SOURCE, "k4", tooLongTitle, "正文"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsMissingIdentity() {
    assertThatThrownBy(
            () -> Notification.create(null, NotificationType.LOW_STOCK, SOURCE, "k5", "标题", "正文"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> Notification.create(RECIPIENT, null, SOURCE, "k6", "标题", "正文"))
        .isInstanceOf(NullPointerException.class);
  }
}
