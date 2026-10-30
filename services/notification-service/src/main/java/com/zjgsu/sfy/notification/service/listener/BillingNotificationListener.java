package com.zjgsu.sfy.notification.service.listener;

import com.zjgsu.sfy.billing.BillPaid;
import com.zjgsu.sfy.notification.common.NotificationType;
import com.zjgsu.sfy.notification.service.NotificationWriter;
import com.zjgsu.sfy.notification.service.PetNotificationTarget;
import com.zjgsu.sfy.notification.service.PetNotificationTargetResolver;
import com.zjgsu.sfy.notification.util.NotificationTexts;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 订阅 billing 的 {@link BillPaid}，为主人生成收费成功通知；重复投递由唯一键去重。 */
@Component
public class BillingNotificationListener {

  private static final Logger log = LoggerFactory.getLogger(BillingNotificationListener.class);

  private final PetNotificationTargetResolver targetResolver;
  private final NotificationWriter notificationWriter;

  public BillingNotificationListener(
      PetNotificationTargetResolver targetResolver, NotificationWriter notificationWriter) {
    this.targetResolver = targetResolver;
    this.notificationWriter = notificationWriter;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onBillPaid(BillPaid event) {
    // 支付已提交；通知写入失败不应把已成功的支付请求变成 500。
    try {
      Optional<PetNotificationTarget> target = targetResolver.resolve(event.petId());
      if (target.isEmpty()) {
        log.atWarn()
            .addKeyValue("eventType", "BILL_PAID")
            .addKeyValue("billId", event.billId())
            .addKeyValue("petId", event.petId())
            .log("notification.target_missing");
        return;
      }
      PetNotificationTarget recipient = target.get();
      notificationWriter.createIfAbsent(
          recipient.recipientUserId(),
          NotificationType.BILL_PAID,
          event.billId(),
          NotificationType.BILL_PAID + ":" + event.billId() + ":" + recipient.recipientUserId(),
          "收费成功",
          "账单 "
              + NotificationTexts.shortId(event.billId())
              + " 已支付 "
              + NotificationTexts.money(event.amount(), event.currency())
              + "（宠物："
              + recipient.petName()
              + "）。");
    } catch (RuntimeException ex) {
      log.atError()
          .addKeyValue("eventType", "BILL_PAID")
          .addKeyValue("billId", event.billId())
          .setCause(ex)
          .log("notification.listener_failed");
    }
  }
}
