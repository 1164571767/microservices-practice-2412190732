package com.zjgsu.sfy.notification.service.listener;

import com.zjgsu.sfy.appointment.AppointmentBooked;
import com.zjgsu.sfy.appointment.QueueNumberCalled;
import com.zjgsu.sfy.identity.IdentityApi;
import com.zjgsu.sfy.identity.UserProfile;
import com.zjgsu.sfy.notification.common.NotificationType;
import com.zjgsu.sfy.notification.service.NotificationWriter;
import com.zjgsu.sfy.notification.service.PetNotificationTarget;
import com.zjgsu.sfy.notification.service.PetNotificationTargetResolver;
import com.zjgsu.sfy.notification.util.NotificationTexts;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 订阅 appointment 事件，为主人生成站内通知。
 *
 * <p>监听在发布方事务提交后执行，并写入独立事务；消费幂等由 {@code dedupe_key} 唯一键保证，重复投递不会产生重复通知。
 */
@Component
public class AppointmentNotificationListener {

  private static final Logger log = LoggerFactory.getLogger(AppointmentNotificationListener.class);

  private final PetNotificationTargetResolver targetResolver;
  private final IdentityApi identityApi;
  private final NotificationWriter notificationWriter;

  public AppointmentNotificationListener(
      PetNotificationTargetResolver targetResolver,
      IdentityApi identityApi,
      NotificationWriter notificationWriter) {
    this.targetResolver = targetResolver;
    this.identityApi = identityApi;
    this.notificationWriter = notificationWriter;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onAppointmentBooked(AppointmentBooked event) {
    // 通知是已提交业务事务的副作用：失败只记录，不能把 500 返回给已经成功的预约请求。
    try {
      Optional<PetNotificationTarget> target = resolve(event.petId(), "APPOINTMENT_BOOKED");
      if (target.isEmpty()) {
        return;
      }
      PetNotificationTarget recipient = target.get();
      String doctorName =
          identityApi.findUser(event.doctorId()).map(UserProfile::displayName).orElse("医生");
      notificationWriter.createIfAbsent(
          recipient.recipientUserId(),
          NotificationType.APPOINTMENT_BOOKED,
          event.appointmentId(),
          dedupeKey(NotificationType.APPOINTMENT_BOOKED, event.appointmentId(), recipient),
          "预约成功",
          "宠物「"
              + recipient.petName()
              + "」已预约 "
              + doctorName
              + "，时间 "
              + NotificationTexts.formatInstant(event.startAt())
              + " 至 "
              + NotificationTexts.formatInstant(event.endAt())
              + "。");
    } catch (RuntimeException ex) {
      log.atError()
          .addKeyValue("eventType", "APPOINTMENT_BOOKED")
          .addKeyValue("appointmentId", event.appointmentId())
          .setCause(ex)
          .log("notification.listener_failed");
    }
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onQueueNumberCalled(QueueNumberCalled event) {
    try {
      Optional<PetNotificationTarget> target = resolve(event.petId(), "QUEUE_CALLED");
      if (target.isEmpty()) {
        return;
      }
      PetNotificationTarget recipient = target.get();
      notificationWriter.createIfAbsent(
          recipient.recipientUserId(),
          NotificationType.QUEUE_CALLED,
          event.appointmentId(),
          dedupeKey(NotificationType.QUEUE_CALLED, event.appointmentId(), recipient),
          "候诊叫号",
          "宠物「" + recipient.petName() + "」已被叫号，请前往诊室就诊。");
    } catch (RuntimeException ex) {
      log.atError()
          .addKeyValue("eventType", "QUEUE_CALLED")
          .addKeyValue("appointmentId", event.appointmentId())
          .setCause(ex)
          .log("notification.listener_failed");
    }
  }

  private Optional<PetNotificationTarget> resolve(UUID petId, String eventType) {
    Optional<PetNotificationTarget> target = targetResolver.resolve(petId);
    if (target.isEmpty()) {
      log.atWarn()
          .addKeyValue("eventType", eventType)
          .addKeyValue("petId", petId)
          .log("notification.target_missing");
    }
    return target;
  }

  private static String dedupeKey(
      NotificationType type, UUID sourceId, PetNotificationTarget recipient) {
    return type.name() + ":" + sourceId + ":" + recipient.recipientUserId();
  }
}
