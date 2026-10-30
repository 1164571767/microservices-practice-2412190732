package com.zjgsu.sfy.notification.service;

import com.zjgsu.sfy.medical.MedicalApi;
import com.zjgsu.sfy.medical.MedicalRecordSummary;
import com.zjgsu.sfy.notification.common.NotificationType;
import com.zjgsu.sfy.notification.common.RevisitReminderProperties;
import com.zjgsu.sfy.notification.util.NotificationTexts;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 复诊提醒生成器。
 *
 * <p>扫描「就诊日 = 提醒日 − delayDays」的病历（经 {@link MedicalApi} 只读），为宠物主人生成一条复诊提醒。 重复执行同一天的任务不会重复生成：同病历 +
 * 同接收人的 {@code dedupe_key} 唯一键由数据库兜底。
 */
@Service
public class RevisitReminderService {

  private static final Logger log = LoggerFactory.getLogger(RevisitReminderService.class);

  private final MedicalApi medicalApi;
  private final PetNotificationTargetResolver targetResolver;
  private final NotificationWriter notificationWriter;
  private final RevisitReminderProperties properties;

  public RevisitReminderService(
      MedicalApi medicalApi,
      PetNotificationTargetResolver targetResolver,
      NotificationWriter notificationWriter,
      RevisitReminderProperties properties) {
    this.medicalApi = medicalApi;
    this.targetResolver = targetResolver;
    this.notificationWriter = notificationWriter;
    this.properties = properties;
  }

  /**
   * 为指定提醒日生成复诊提醒。
   *
   * @param reminderDate 提醒日（任务执行日），实际扫描其前 {@code delayDays} 天就诊的病历
   * @return 新建通知条数（重复执行时为 0）
   */
  public int generateFor(LocalDate reminderDate) {
    Objects.requireNonNull(reminderDate, "reminderDate must not be null");
    ZoneId zone = properties.zoneId();
    LocalDate visitDate = reminderDate.minusDays(properties.delayDays());
    Instant fromInclusive = visitDate.atStartOfDay(zone).toInstant();
    Instant toExclusive = visitDate.plusDays(1).atStartOfDay(zone).toInstant();
    List<MedicalRecordSummary> records =
        medicalApi.findRecordsVisitedBetween(fromInclusive, toExclusive);

    int created = 0;
    for (MedicalRecordSummary record : records) {
      Optional<PetNotificationTarget> target = targetResolver.resolve(record.petId());
      if (target.isEmpty()) {
        log.atWarn()
            .addKeyValue("recordId", record.recordId())
            .addKeyValue("petId", record.petId())
            .log("notification.revisit_reminder.target_missing");
        continue;
      }
      PetNotificationTarget recipient = target.get();
      String body =
          "宠物「"
              + recipient.petName()
              + "」于 "
              + NotificationTexts.formatInstant(record.visitedAt())
              + " 到院就诊，已满 "
              + properties.delayDays()
              + " 天，建议安排复诊。";
      boolean inserted =
          notificationWriter
              .createIfAbsent(
                  recipient.recipientUserId(),
                  NotificationType.REVISIT_REMINDER,
                  record.recordId(),
                  NotificationType.REVISIT_REMINDER
                      + ":"
                      + record.recordId()
                      + ":"
                      + recipient.recipientUserId(),
                  "复诊提醒",
                  body)
              .isPresent();
      if (inserted) {
        created++;
      }
    }
    log.atInfo()
        .addKeyValue("reminderDate", reminderDate)
        .addKeyValue("visitDate", visitDate)
        .addKeyValue("visitedRecords", records.size())
        .addKeyValue("created", created)
        .log("notification.revisit_reminder.completed");
    return created;
  }

  /** 每日定时入口；关闭开关后只记录日志、不生成提醒。 */
  @Scheduled(
      cron = "${petcare.notification.revisit-reminder.cron:0 0 9 * * *}",
      zone = "${petcare.notification.revisit-reminder.zone:Asia/Shanghai}")
  public void generateDaily() {
    if (!properties.enabled()) {
      log.atInfo().log("notification.revisit_reminder.disabled");
      return;
    }
    generateFor(LocalDate.now(properties.zoneId()));
  }
}
