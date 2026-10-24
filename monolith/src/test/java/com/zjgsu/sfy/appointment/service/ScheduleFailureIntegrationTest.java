package com.zjgsu.sfy.appointment.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.zjgsu.sfy.appointment.common.ScheduleState;
import com.zjgsu.sfy.appointment.repository.ScheduleLedgerRepository;
import com.zjgsu.sfy.appointment.service.command.CreateScheduleCommand;
import com.zjgsu.sfy.appointment.support.AppointmentIntegrationTest;
import com.zjgsu.sfy.identity.CurrentUser;
import com.zjgsu.sfy.identity.IdentityApi;
import com.zjgsu.sfy.identity.common.Role;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** Explicit injected dependency failures; all other writes use real MySQL transactions. */
class ScheduleFailureIntegrationTest extends AppointmentIntegrationTest {
  @MockitoSpyBean ScheduleLedgerRepository ledger;
  @MockitoSpyBean IdentityApi identity;
  @Autowired DoctorScheduleService schedules;

  CurrentUser admin() {
    var u = seedUser(Set.of(Role.ADMIN));
    return new CurrentUser(u.getId(), u.getUsername(), u.getRoles());
  }

  @Test
  void identityDependencyFailureDoesNotCreateSchedule() {
    var d = seedUser(Set.of(Role.DOCTOR));
    var actor = admin();
    doThrow(new IllegalStateException("injected identity unavailable"))
        .when(identity)
        .getUser(d.getId());
    assertThatThrownBy(
            () ->
                schedules.createSchedule(
                    actor, new CreateScheduleCommand(d.getId(), SLOT_START, SCHEDULE_END, 30)))
        .isInstanceOf(IllegalStateException.class);
    assertThat(scheduleRepository.findByDoctorIdOrderByStartAtAsc(d.getId())).isEmpty();
  }

  @Test
  void auditStorageFailureRollsBackCreatedSchedule() {
    var d = seedUser(Set.of(Role.DOCTOR));
    var actor = admin();
    doThrow(new IllegalStateException("injected audit storage unavailable"))
        .when(ledger)
        .audit(any(), any(), anyString(), anyString(), any());
    assertThatThrownBy(
            () ->
                schedules.createSchedule(
                    actor, new CreateScheduleCommand(d.getId(), SLOT_START, SCHEDULE_END, 30)))
        .isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class)
        .hasRootCauseInstanceOf(IllegalStateException.class);
    assertThat(scheduleRepository.findByDoctorIdOrderByStartAtAsc(d.getId())).isEmpty();
  }

  @Test
  void auditStorageFailureRollsBackStopBooking() {
    var d = seedUser(Set.of(Role.DOCTOR));
    var s = seedSchedule(d.getId());
    var actor = admin();
    doThrow(new IllegalStateException("injected audit storage unavailable"))
        .when(ledger)
        .audit(any(), any(), anyString(), anyString(), any());
    assertThatThrownBy(
            () -> schedules.changeState(actor, s.getId(), 0, "暂停", ScheduleState.BOOKING_CLOSED))
        .isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class)
        .hasRootCauseInstanceOf(IllegalStateException.class);
    assertThat(scheduleRepository.findById(s.getId()).orElseThrow().getState())
        .isEqualTo(ScheduleState.OPEN);
    assertThat(schedules.operations(actor, s.getId(), 0)).isEmpty();
  }
}
