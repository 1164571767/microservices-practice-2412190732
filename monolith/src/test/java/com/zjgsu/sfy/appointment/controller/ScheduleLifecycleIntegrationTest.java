package com.zjgsu.sfy.appointment.controller;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.zjgsu.sfy.appointment.common.ScheduleState;
import com.zjgsu.sfy.appointment.entity.DoctorSchedule;
import com.zjgsu.sfy.appointment.service.AppointmentService;
import com.zjgsu.sfy.appointment.service.DoctorScheduleService;
import com.zjgsu.sfy.appointment.service.command.BookAppointmentCommand;
import com.zjgsu.sfy.appointment.service.command.CreateScheduleCommand;
import com.zjgsu.sfy.appointment.service.command.RescheduleCommand;
import com.zjgsu.sfy.appointment.support.AppointmentIntegrationTest;
import com.zjgsu.sfy.common.error.BusinessException;
import com.zjgsu.sfy.identity.CurrentUser;
import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.entity.User;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class ScheduleLifecycleIntegrationTest extends AppointmentIntegrationTest {
  @Autowired DoctorScheduleService schedules;
  @Autowired AppointmentService appointments;

  CurrentUser actor(User u) {
    return new CurrentUser(u.getId(), u.getUsername(), u.getRoles());
  }

  DoctorSchedule future(User doctor) {
    return scheduleRepository.saveAndFlush(
        DoctorSchedule.create(doctor.getId(), SLOT_START.plusSeconds(3600), SCHEDULE_END, 30));
  }

  CurrentUser admin() {
    return actor(seedUser(Set.of(Role.ADMIN)));
  }

  @Test
  void stopAndReopenKeepAppointmentsAndAudit() {
    User doctor = seedUser(Set.of(Role.DOCTOR));
    DoctorSchedule s = seedSchedule(doctor.getId());
    User owner = seedUser(Set.of(Role.OWNER));
    var pet = seedPet(seedOwner(owner));
    var a =
        appointments.book(
            actor(owner),
            new BookAppointmentCommand(pet.getId(), doctor.getId(), SLOT_START, SLOT_END),
            null);
    CurrentUser admin = admin();
    var stopped = schedules.changeState(admin, s.getId(), 0, "暂停新增", ScheduleState.BOOKING_CLOSED);
    assertThat(schedules.availability(doctor.getId(), SLOT_DATE)).isEmpty();
    assertThat(appointmentRepository.findById(a.getId()).orElseThrow().getScheduleId())
        .isEqualTo(s.getId());
    assertThatThrownBy(
            () ->
                appointments.book(
                    actor(owner),
                    new BookAppointmentCommand(
                        pet.getId(), doctor.getId(), SLOT_END, SLOT_END.plusSeconds(1800)),
                    null))
        .isInstanceOf(BusinessException.class);
    var reopened =
        schedules.changeState(admin, s.getId(), stopped.getVersion(), "继续接诊", ScheduleState.OPEN);
    assertThat(reopened.getState()).isEqualTo(ScheduleState.OPEN);
    assertThat(schedules.availability(doctor.getId(), SLOT_DATE)).hasSize(7);
    assertThat(schedules.operations(admin, s.getId(), 0))
        .extracting(o -> o.reason())
        .containsExactlyInAnyOrder("继续接诊", "暂停新增");
  }

  @Test
  void cancelRequiresProcessingActiveAppointmentsAndRetainsCancelledHistory() {
    User d = seedUser(Set.of(Role.DOCTOR));
    var s = future(d);
    CurrentUser admin = admin();
    User owner = seedUser(Set.of(Role.OWNER));
    var pet = seedPet(seedOwner(owner));
    var a =
        appointments.book(
            actor(owner),
            new BookAppointmentCommand(
                pet.getId(), d.getId(), s.getStartAt(), s.getStartAt().plusSeconds(1800)),
            null);
    assertThatThrownBy(
            () -> schedules.changeState(admin, s.getId(), 0, "停诊", ScheduleState.CANCELLED))
        .isInstanceOf(BusinessException.class);
    appointments.cancel(actor(owner), a.getId());
    var cancelled = schedules.changeState(admin, s.getId(), 0, "已人工处理", ScheduleState.CANCELLED);
    assertThatThrownBy(
            () -> schedules.deleteSchedule(admin, s.getId(), cancelled.getVersion(), "删除"))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(
            () ->
                schedules.changeState(
                    admin, s.getId(), cancelled.getVersion(), "恢复", ScheduleState.OPEN))
        .isInstanceOf(BusinessException.class);
    var replacement =
        schedules.createSchedule(
            admin, new CreateScheduleCommand(d.getId(), s.getStartAt(), s.getEndAt(), 30));
    assertThat(replacement.getId()).isNotEqualTo(s.getId());
  }

  @Test
  void checkedInAndCalledAppointmentsPreventCancellation() {
    for (boolean called : List.of(false, true)) {
      User d = seedUser(Set.of(Role.DOCTOR));
      var s = seedSchedule(d.getId());
      var a = seedAppointment(UUID.randomUUID(), d.getId());
      a.checkIn(SLOT_START);
      if (called) a.call(SLOT_START);
      appointmentRepository.saveAndFlush(a);
      assertThatThrownBy(
              () -> schedules.changeState(admin(), s.getId(), 0, "停诊", ScheduleState.CANCELLED))
          .isInstanceOf(BusinessException.class);
    }
  }

  @Test
  void deleteEmptyFutureScheduleKeepsAuditAndRejectsStartedOrHistorical() {
    User d = seedUser(Set.of(Role.DOCTOR));
    var s = future(d);
    CurrentUser admin = admin();
    schedules.deleteSchedule(admin, s.getId(), 0, "录入错误");
    assertThat(scheduleRepository.findById(s.getId())).isEmpty();
    assertThat(schedules.operations(admin, s.getId(), 0)).hasSize(1);
    var started = seedSchedule(d.getId());
    assertThatThrownBy(() -> schedules.deleteSchedule(admin, started.getId(), 0, "删除"))
        .isInstanceOf(BusinessException.class);
    var historical =
        scheduleRepository.saveAndFlush(
            DoctorSchedule.create(
                d.getId(), SLOT_START.minusSeconds(86400), SLOT_START.minusSeconds(80000), 30));
    assertThatThrownBy(
            () ->
                schedules.changeState(
                    admin, historical.getId(), 0, "关闭", ScheduleState.BOOKING_CLOSED))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void reschedulingPreservesOriginalScheduleHistoryAndClosedTargetRejectsChanges() {
    User d = seedUser(Set.of(Role.DOCTOR));
    var first = future(d);
    var second =
        scheduleRepository.saveAndFlush(
            DoctorSchedule.create(d.getId(), SCHEDULE_END, SCHEDULE_END.plusSeconds(3600), 30));
    User owner = seedUser(Set.of(Role.OWNER));
    var pet = seedPet(seedOwner(owner));
    CurrentUser admin = admin();
    var a =
        appointments.book(
            actor(owner),
            new BookAppointmentCommand(
                pet.getId(), d.getId(), first.getStartAt(), first.getStartAt().plusSeconds(1800)),
            null);
    var closed =
        schedules.changeState(admin, second.getId(), 0, "暂停", ScheduleState.BOOKING_CLOSED);
    assertThatThrownBy(
            () ->
                appointments.reschedule(
                    actor(owner),
                    a.getId(),
                    new RescheduleCommand(second.getStartAt(), second.getEndAt())))
        .isInstanceOf(BusinessException.class);
    schedules.changeState(admin, second.getId(), closed.getVersion(), "恢复", ScheduleState.OPEN);
    appointments.reschedule(
        actor(owner), a.getId(), new RescheduleCommand(second.getStartAt(), second.getEndAt()));
    assertThatThrownBy(() -> schedules.deleteSchedule(admin, first.getId(), 0, "删除旧排班"))
        .isInstanceOf(BusinessException.class);
    assertThat(appointmentRepository.findById(a.getId()).orElseThrow().getScheduleId())
        .isEqualTo(second.getId());
  }

  @Test
  void staleVersionAndLegacyUnlinkedAppointmentsAreProtected() {
    User d = seedUser(Set.of(Role.DOCTOR));
    var s = future(d);
    CurrentUser admin = admin();
    schedules.changeState(admin, s.getId(), 0, "暂停", ScheduleState.BOOKING_CLOSED);
    assertThatThrownBy(
            () -> schedules.changeState(admin, s.getId(), 0, "旧页面恢复", ScheduleState.OPEN))
        .isInstanceOf(BusinessException.class);
    seedAppointment(UUID.randomUUID(), d.getId(), s.getStartAt(), s.getEndAt());
    assertThatThrownBy(() -> schedules.deleteSchedule(admin, s.getId(), 1, "删除"))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(
            () -> schedules.changeState(admin, s.getId(), 1, "停诊", ScheduleState.CANCELLED))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void permissionsValidationAndHttpLifecycle() throws Exception {
    User d = seedUser(Set.of(Role.DOCTOR)), admin = seedUser(Set.of(Role.ADMIN));
    var s = future(d);
    String path = "/api/appointment/schedules/" + s.getId();
    for (Role role : List.of(Role.STAFF, Role.DOCTOR, Role.PHARMACY, Role.OWNER)) {
      User user = seedUser(Set.of(role));
      mockMvc
          .perform(
              post(path + "/stop-booking")
                  .header("Authorization", bearer(user))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"version\":0,\"reason\":\"暂停\"}"))
          .andExpect(status().isForbidden());
      mockMvc
          .perform(
              delete(path)
                  .param("version", "0")
                  .param("reason", "错误")
                  .header("Authorization", bearer(user)))
          .andExpect(status().isForbidden());
    }
    mockMvc
        .perform(
            post(path + "/stop-booking")
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"reason\":\"  \"}"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            delete(path)
                .param("version", "-1")
                .param("reason", "错误")
                .header("Authorization", bearer(admin)))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            post(path + "/stop-booking")
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"reason\":\"暂停放号\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("BOOKING_CLOSED"))
        .andExpect(jsonPath("$.version").value(1));
    mockMvc
        .perform(get(path + "/operations").header("Authorization", bearer(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].reason").value("暂停放号"));
  }

  @Test
  void firstEverOverlappingSchedulesAreSerialized() throws Exception {
    User d = seedUser(Set.of(Role.DOCTOR));
    CurrentUser admin = admin();
    var pool = Executors.newFixedThreadPool(2);
    var barrier = new CyclicBarrier(2);
    try {
      var results =
          pool.invokeAll(
              List.<Callable<Boolean>>of(
                  () -> create(barrier, admin, d.getId(), SLOT_START),
                  () -> create(barrier, admin, d.getId(), SLOT_START.plusSeconds(60))));
      assertThat(List.of(results.get(0).get(), results.get(1).get()))
          .containsExactlyInAnyOrder(true, false);
    } finally {
      pool.shutdownNow();
    }
  }

  boolean create(CyclicBarrier b, CurrentUser admin, UUID doctor, Instant start) throws Exception {
    b.await(10, TimeUnit.SECONDS);
    try {
      schedules.createSchedule(
          admin, new CreateScheduleCommand(doctor, start, start.plusSeconds(3600), 30));
      return true;
    } catch (BusinessException e) {
      return false;
    }
  }

  @Test
  void bookingVersusCancellationNeverLeavesAppointmentOnCancelledSchedule() throws Exception {
    User d = seedUser(Set.of(Role.DOCTOR));
    var s = future(d);
    CurrentUser admin = admin();
    User owner = seedUser(Set.of(Role.OWNER));
    var pet = seedPet(seedOwner(owner));
    var pool = Executors.newFixedThreadPool(2);
    var barrier = new CyclicBarrier(2);
    try {
      var results =
          pool.invokeAll(
              List.<Callable<Boolean>>of(
                  () -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    try {
                      appointments.book(
                          actor(owner),
                          new BookAppointmentCommand(
                              pet.getId(),
                              d.getId(),
                              s.getStartAt(),
                              s.getStartAt().plusSeconds(1800)),
                          null);
                      return true;
                    } catch (BusinessException e) {
                      return false;
                    }
                  },
                  () -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    try {
                      schedules.changeState(admin, s.getId(), 0, "停诊", ScheduleState.CANCELLED);
                      return true;
                    } catch (BusinessException e) {
                      return false;
                    }
                  }));
      assertThat(List.of(results.get(0).get(), results.get(1).get()))
          .containsExactlyInAnyOrder(true, false);
    } finally {
      pool.shutdownNow();
    }
  }
}
