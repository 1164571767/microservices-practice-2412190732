package com.zjgsu.sfy.notification.service;

import com.zjgsu.sfy.notification.common.NotificationType;
import com.zjgsu.sfy.notification.entity.Notification;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 幂等写入站内通知。
 *
 * <p>同一条事件 / 提醒对同一接收人只落一条：{@code dedupe_key} 唯一键是数据库最终守卫。写入在独立事务中完成，唯一键冲突意味着
 * 已被其它线程或更早的重复投递消费，返回空而不报错。
 */
@Service
public class NotificationWriter {

  private static final Logger log = LoggerFactory.getLogger(NotificationWriter.class);

  private final NotificationInserter inserter;
  private final MeterRegistry meterRegistry;

  public NotificationWriter(NotificationInserter inserter, MeterRegistry meterRegistry) {
    this.inserter = inserter;
    this.meterRegistry = meterRegistry;
  }

  /**
   * @return 新建的通知；{@code dedupeKey} 已存在时返回空（视为重复消费）。
   */
  public Optional<Notification> createIfAbsent(
      UUID recipientUserId,
      NotificationType type,
      UUID sourceId,
      String dedupeKey,
      String title,
      String body) {
    try {
      Notification notification =
          inserter.insert(recipientUserId, type, sourceId, dedupeKey, title, body);
      counterOf(type).increment();
      log.atInfo()
          .addKeyValue("notificationId", notification.getId())
          .addKeyValue("type", type)
          .addKeyValue("recipientUserId", recipientUserId)
          .addKeyValue("sourceId", sourceId)
          .log("notification.created");
      return Optional.of(notification);
    } catch (DataIntegrityViolationException ex) {
      log.atInfo()
          .addKeyValue("type", type)
          .addKeyValue("recipientUserId", recipientUserId)
          .addKeyValue("sourceId", sourceId)
          .log("notification.duplicate_ignored");
      return Optional.empty();
    }
  }

  private Counter counterOf(NotificationType type) {
    return Counter.builder("petcare.notification.created")
        .tag("type", type.name())
        .register(meterRegistry);
  }
}
