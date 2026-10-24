package com.zjgsu.sfy.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.entity.User;
import com.zjgsu.sfy.medical.entity.MedicalRecord;
import com.zjgsu.sfy.notification.common.NotificationType;
import com.zjgsu.sfy.notification.entity.Notification;
import com.zjgsu.sfy.notification.support.NotificationIntegrationTest;
import com.zjgsu.sfy.owner.entity.Pet;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 复诊提醒：按就诊日 + 配置天数生成，重复执行幂等。 */
class RevisitReminderServiceTest extends NotificationIntegrationTest {

  private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
  private static final LocalDate REMINDER_DATE = LocalDate.of(2026, 11, 20);
  private static final int DELAY_DAYS = 7;

  @Test
  void generatesReminderForVisitOnConfiguredDelayDay() {
    User owner = seedUser(Set.of(Role.OWNER));
    Pet pet = seedPet(seedOwner(owner));
    User doctor = seedDoctor();
    MedicalRecord record = seedRecord(pet.getId(), doctor.getId(), visitedAt(-DELAY_DAYS), "诊断");

    int created = revisitReminderService.generateFor(REMINDER_DATE);

    assertThat(created).isGreaterThanOrEqualTo(1);
    Notification notification =
        onlyNotificationOf(owner.getId(), NotificationType.REVISIT_REMINDER);
    assertThat(notification.getSourceId()).isEqualTo(record.getId());
    assertThat(notification.getTitle()).isEqualTo("复诊提醒");
    assertThat(notification.getBody()).contains(pet.getName()).contains("已满 7 天");
    assertThat(notification.getDedupeKey()).startsWith("REVISIT_REMINDER:");
  }

  @Test
  void rerunningTheSameDayDoesNotCreateDuplicateReminders() {
    User owner = seedUser(Set.of(Role.OWNER));
    Pet pet = seedPet(seedOwner(owner));
    User doctor = seedDoctor();
    seedRecord(pet.getId(), doctor.getId(), visitedAt(-DELAY_DAYS), "诊断");

    assertThat(revisitReminderService.generateFor(REMINDER_DATE)).isGreaterThanOrEqualTo(1);
    assertThat(revisitReminderService.generateFor(REMINDER_DATE)).isZero();
    assertThat(notificationsOf(owner.getId(), NotificationType.REVISIT_REMINDER)).hasSize(1);
  }

  @Test
  void visitsOutsideTheWindowAreNotReminded() {
    User owner = seedUser(Set.of(Role.OWNER));
    Pet pet = seedPet(seedOwner(owner));
    User doctor = seedDoctor();
    seedRecord(pet.getId(), doctor.getId(), visitedAt(-DELAY_DAYS - 1), "诊断");
    seedRecord(pet.getId(), doctor.getId(), visitedAt(-DELAY_DAYS + 1), "诊断");

    revisitReminderService.generateFor(REMINDER_DATE);

    assertThat(notificationsOf(owner.getId(), NotificationType.REVISIT_REMINDER)).isEmpty();
  }

  private static Instant visitedAt(int daysOffset) {
    return REMINDER_DATE.plusDays(daysOffset).atTime(10, 0).atZone(ZONE).toInstant();
  }
}
