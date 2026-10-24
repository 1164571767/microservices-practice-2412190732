package com.zjgsu.sfy.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.zjgsu.sfy.appointment.entity.Appointment;
import com.zjgsu.sfy.billing.BillPaid;
import com.zjgsu.sfy.billing.entity.ChargeItem;
import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.entity.User;
import com.zjgsu.sfy.medical.entity.MedicalRecord;
import com.zjgsu.sfy.notification.common.NotificationStatus;
import com.zjgsu.sfy.notification.common.NotificationType;
import com.zjgsu.sfy.notification.entity.Notification;
import com.zjgsu.sfy.notification.support.NotificationIntegrationTest;
import com.zjgsu.sfy.owner.entity.Pet;
import com.zjgsu.sfy.pharmacy.LowStockDetected;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** 事件订阅与消费幂等：预约成功 / 收费成功 / 叫号 / 低库存 → 站内通知。 */
class NotificationEventIntegrationTest extends NotificationIntegrationTest {

  @Test
  void bookingAppointmentNotifiesOwnerAndIsVisibleInOwnInbox() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));
    Pet pet = seedPet(seedOwner(owner));
    User doctor = seedDoctor();
    seedSchedule(doctor.getId());

    mockMvc
        .perform(
            post("/api/appointment/appointments")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookBody(pet.getId(), doctor.getId(), SLOT_START, SLOT_END)))
        .andExpect(status().isCreated());

    Notification notification =
        onlyNotificationOf(owner.getId(), NotificationType.APPOINTMENT_BOOKED);
    assertThat(notification.getTitle()).isEqualTo("预约成功");
    assertThat(notification.getBody()).contains(pet.getName()).contains(doctor.getDisplayName());
    assertThat(notification.getStatus()).isEqualTo(NotificationStatus.UNREAD);
    assertThat(notification.getDedupeKey()).startsWith("APPOINTMENT_BOOKED:");

    mockMvc
        .perform(
            get("/api/notification/notifications").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].type").value("APPOINTMENT_BOOKED"))
        .andExpect(jsonPath("$.items[0].status").value("UNREAD"));
  }

  @Test
  void payingBillNotifiesOwner() throws Exception {
    User staff = seedStaff();
    User doctor = seedDoctor();
    User owner = seedUser(Set.of(Role.OWNER));
    Pet pet = seedPet(seedOwner(owner));
    MedicalRecord record = seedRecordFor(pet, doctor);
    ChargeItem item = seedChargeItem("80.00");

    String created =
        createBill(staff, billBody(record.getId(), null, List.of(line(item.getId(), 1))), null);
    UUID billId = UUID.fromString(JsonPath.read(created, "$.id"));

    payBill(staff, billId, null);

    Notification notification = onlyNotificationOf(owner.getId(), NotificationType.BILL_PAID);
    assertThat(notification.getSourceId()).isEqualTo(billId);
    assertThat(notification.getBody()).contains("已支付").contains("80.00").contains(pet.getName());
  }

  @Test
  void queueCallNotifiesOwner() throws Exception {
    User staff = seedStaff();
    User doctor = seedDoctor();
    User owner = seedUser(Set.of(Role.OWNER));
    Pet pet = seedPet(seedOwner(owner));
    // This notification test starts before check-in; medical fixtures otherwise start at CALLED.
    Appointment appointment =
        appointmentRepository.saveAndFlush(
            Appointment.book(pet.getId(), doctor.getId(), SLOT_START, SLOT_END));

    mockMvc
        .perform(
            post("/api/appointment/appointments/" + appointment.getId() + "/check-in")
                .header(HttpHeaders.AUTHORIZATION, bearer(staff)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/appointment/queue/call")
                .param("doctorId", doctor.getId().toString())
                .header(HttpHeaders.AUTHORIZATION, bearer(staff)))
        .andExpect(status().isOk());

    Notification notification = onlyNotificationOf(owner.getId(), NotificationType.QUEUE_CALLED);
    assertThat(notification.getSourceId()).isEqualTo(appointment.getId());
    assertThat(notification.getBody()).contains(pet.getName());
  }

  @Test
  void duplicateBillPaidDeliveryCreatesSingleNotification() {
    User owner = seedUser(Set.of(Role.OWNER));
    Pet pet = seedPet(seedOwner(owner));
    BillPaid event =
        new BillPaid(
            UUID.randomUUID(),
            UUID.randomUUID(),
            pet.getId(),
            UUID.randomUUID(),
            null,
            new BigDecimal("50.00"),
            "CNY",
            owner.getId(),
            Instant.now());

    eventPublisher.publishEvent(event);
    eventPublisher.publishEvent(event);

    assertThat(notificationsOf(owner.getId(), NotificationType.BILL_PAID))
        .filteredOn(notification -> notification.getSourceId().equals(event.billId()))
        .hasSize(1);
  }

  @Test
  void duplicateLowStockDeliveryNotifiesEachRecipientOnce() {
    User pharmacist = seedUser(Set.of(Role.PHARMACY));
    User admin = seedUser(Set.of(Role.ADMIN));
    LowStockDetected event =
        new LowStockDetected(
            UUID.randomUUID(),
            "药" + UUID.randomUUID().toString().replace("-", "").substring(0, 8),
            "0.5g",
            2,
            5,
            Instant.now());

    eventPublisher.publishEvent(event);
    eventPublisher.publishEvent(event);

    Notification pharmacistNotification =
        onlyNotificationOf(pharmacist.getId(), NotificationType.LOW_STOCK);
    assertThat(pharmacistNotification.getBody()).contains("低于阈值 5").contains("请及时补货");
    assertThat(notificationsOf(admin.getId(), NotificationType.LOW_STOCK)).hasSize(1);
  }

  @Test
  void eventForUnknownPetIsIgnoredWithoutFailing() {
    BillPaid event =
        new BillPaid(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            new BigDecimal("1.00"),
            "CNY",
            UUID.randomUUID(),
            Instant.now());

    assertThatCode(() -> eventPublisher.publishEvent(event)).doesNotThrowAnyException();
  }
}
