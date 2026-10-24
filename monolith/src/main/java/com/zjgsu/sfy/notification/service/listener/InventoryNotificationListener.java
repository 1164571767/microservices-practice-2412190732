package com.zjgsu.sfy.notification.service.listener;

import com.zjgsu.sfy.identity.IdentityApi;
import com.zjgsu.sfy.notification.common.NotificationType;
import com.zjgsu.sfy.notification.service.NotificationWriter;
import com.zjgsu.sfy.pharmacy.LowStockDetected;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 订阅 pharmacy 的 {@link LowStockDetected}，向药房 / 管理员角色广播低库存预警。
 *
 * <p>角色广播经 {@link IdentityApi#findActiveUserIdsInRoles} 解析接收人；同一段低库存期间重复投递不会重复通知， 补货后再次跌破阈值会因
 * {@code detectedAt} 不同而生成新通知。
 */
@Component
public class InventoryNotificationListener {

  private static final Logger log = LoggerFactory.getLogger(InventoryNotificationListener.class);
  private static final List<String> LOW_STOCK_ROLE_NAMES = List.of("PHARMACY", "ADMIN");

  private final IdentityApi identityApi;
  private final NotificationWriter notificationWriter;

  public InventoryNotificationListener(
      IdentityApi identityApi, NotificationWriter notificationWriter) {
    this.identityApi = identityApi;
    this.notificationWriter = notificationWriter;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onLowStockDetected(LowStockDetected event) {
    // 库存变更已提交；广播失败只记日志，不回滚也不改变已成功的发药/调整响应。
    try {
      List<UUID> recipients = identityApi.findActiveUserIdsInRoles(LOW_STOCK_ROLE_NAMES);
      if (recipients.isEmpty()) {
        log.atWarn()
            .addKeyValue("eventType", "LOW_STOCK")
            .addKeyValue("drugId", event.drugId())
            .log("notification.no_recipients");
        return;
      }
      String body =
          "药品「"
              + event.drugName()
              + " "
              + event.specification()
              + "」库存 "
              + event.quantity()
              + "，低于阈值 "
              + event.threshold()
              + "，请及时补货。";
      long occurrence = event.detectedAt().toEpochMilli();
      for (UUID recipientUserId : recipients) {
        notificationWriter.createIfAbsent(
            recipientUserId,
            NotificationType.LOW_STOCK,
            event.drugId(),
            NotificationType.LOW_STOCK
                + ":"
                + event.drugId()
                + ":"
                + occurrence
                + ":"
                + recipientUserId,
            "低库存预警",
            body);
      }
    } catch (RuntimeException ex) {
      log.atError()
          .addKeyValue("eventType", "LOW_STOCK")
          .addKeyValue("drugId", event.drugId())
          .setCause(ex)
          .log("notification.listener_failed");
    }
  }
}
