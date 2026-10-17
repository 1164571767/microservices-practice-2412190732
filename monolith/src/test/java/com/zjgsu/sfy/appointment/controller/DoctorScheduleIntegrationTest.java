package com.zjgsu.sfy.appointment.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zjgsu.sfy.appointment.support.AppointmentIntegrationTest;
import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.entity.User;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class DoctorScheduleIntegrationTest extends AppointmentIntegrationTest {

  @Test
  void adminCreatesAndListsSchedule() throws Exception {
    User admin = seedUser(Set.of(Role.ADMIN));
    User doctor = seedUser(Set.of(Role.DOCTOR));

    mockMvc
        .perform(
            post("/api/appointment/schedules")
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(scheduleBody(doctor.getId(), SCHEDULE_START, SCHEDULE_END, 30)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.doctorId").value(doctor.getId().toString()))
        .andExpect(jsonPath("$.slotMinutes").value(30));

    mockMvc
        .perform(
            get("/api/appointment/schedules")
                .param("doctorId", doctor.getId().toString())
                .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void nonAdminCannotCreateSchedule() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));
    User doctor = seedUser(Set.of(Role.DOCTOR));

    mockMvc
        .perform(
            post("/api/appointment/schedules")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(scheduleBody(doctor.getId(), SCHEDULE_START, SCHEDULE_END, 30)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
  }

  @Test
  void overlappingScheduleIsRejected() throws Exception {
    User admin = seedUser(Set.of(Role.ADMIN));
    User doctor = seedUser(Set.of(Role.DOCTOR));
    seedSchedule(doctor.getId());

    mockMvc
        .perform(
            post("/api/appointment/schedules")
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    scheduleBody(
                        doctor.getId(),
                        Instant.parse("2026-11-10T02:00:00Z"),
                        Instant.parse("2026-11-10T06:00:00Z"),
                        30)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("APPOINTMENT_CONFLICT"));
  }

  @Test
  void scheduleForNonDoctorIsRejected() throws Exception {
    User admin = seedUser(Set.of(Role.ADMIN));
    User notDoctor = seedUser(Set.of(Role.OWNER));

    mockMvc
        .perform(
            post("/api/appointment/schedules")
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(scheduleBody(notDoctor.getId(), SCHEDULE_START, SCHEDULE_END, 30)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("OPERATION_NOT_ALLOWED"));
  }

  @Test
  void invalidRangeIsRejected() throws Exception {
    User admin = seedUser(Set.of(Role.ADMIN));
    User doctor = seedUser(Set.of(Role.DOCTOR));

    mockMvc
        .perform(
            post("/api/appointment/schedules")
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(scheduleBody(doctor.getId(), SCHEDULE_END, SCHEDULE_START, 30)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void availabilitySplitsSlotsAndExcludesBooked() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));
    User doctor = seedUser(Set.of(Role.DOCTOR));
    seedSchedule(doctor.getId());
    seedAppointment(UUID.randomUUID(), doctor.getId());

    // 排班 4h、30min 一格 = 8 格；SLOT_START 已被占用 → 7 格。
    mockMvc
        .perform(
            get("/api/appointment/availability")
                .param("doctorId", doctor.getId().toString())
                .param("date", SLOT_DATE.toString())
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(7));
  }
}
