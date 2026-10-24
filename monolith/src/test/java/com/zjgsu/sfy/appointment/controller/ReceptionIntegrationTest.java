package com.zjgsu.sfy.appointment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.zjgsu.sfy.appointment.common.AppointmentStatus;
import com.zjgsu.sfy.appointment.support.AppointmentIntegrationTest;
import com.zjgsu.sfy.identity.common.Role;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class ReceptionIntegrationTest extends AppointmentIntegrationTest {
  @Test
  void doctorDirectoryExposesOnlyChoiceFields() throws Exception {
    var doctor = seedUser(Set.of(Role.DOCTOR));
    var staff = seedUser(Set.of(Role.STAFF));
    String response =
        mockMvc
            .perform(
                get("/api/appointment/doctors").header(HttpHeaders.AUTHORIZATION, bearer(staff)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    var rows = objectMapper.readTree(response);
    assertThat(rows.size()).isPositive();
    boolean found = false;
    for (var row : rows) {
      assertThat(row.size()).isEqualTo(2);
      assertThat(row.has("phone")).isFalse();
      if (row.get("id").asText().equals(doctor.getId().toString())) found = true;
    }
    assertThat(found).isTrue();
  }

  @Test
  void receptionDateBoundariesFiltersPagingAndUnboundPatient() throws Exception {
    var staff = seedUser(Set.of(Role.STAFF));
    var doctor = seedUser(Set.of(Role.DOCTOR));
    var owner =
        ownerRepository.saveAndFlush(
            com.zjgsu.sfy.owner.entity.Owner.createUnbound("未绑定接待客户", uniquePhone(), null));
    var pet = seedPet(owner);
    // 上海日期从前一天 UTC 16:00 开始，终点不包含次日零点。
    seedAppointment(
        pet.getId(),
        doctor.getId(),
        Instant.parse("2026-11-09T16:00:00Z"),
        Instant.parse("2026-11-09T16:30:00Z"));
    seedAppointment(
        pet.getId(),
        doctor.getId(),
        Instant.parse("2026-11-10T15:30:00Z"),
        Instant.parse("2026-11-10T16:00:00Z"));
    seedAppointment(
        pet.getId(),
        doctor.getId(),
        Instant.parse("2026-11-10T16:00:00Z"),
        Instant.parse("2026-11-10T16:30:00Z"));
    mockMvc
        .perform(
            get("/api/appointment/appointments")
                .param("date", "2026-11-10")
                .param("doctorId", doctor.getId().toString())
                .param("petId", pet.getId().toString())
                .param("status", "SCHEDULED")
                .param("page", "1")
                .param("size", "1")
                .header(HttpHeaders.AUTHORIZATION, bearer(staff)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].patient.ownerName").value("未绑定接待客户"))
        .andExpect(jsonPath("$.items[0].patient.petId").value(pet.getId().toString()))
        .andExpect(jsonPath("$.items[0].appointment.startAt").value("2026-11-10T15:30:00Z"))
        .andExpect(jsonPath("$.items[0].patient.diagnosis").doesNotExist());
  }

  @Test
  void allThreeClinicRolesMayReadAndForeignRolesCannot() throws Exception {
    for (var role : new Role[] {Role.STAFF, Role.DOCTOR, Role.ADMIN, Role.OWNER, Role.PHARMACY}) {
      var user = seedUser(Set.of(role));
      boolean allowed = role == Role.STAFF || role == Role.DOCTOR || role == Role.ADMIN;
      for (String path :
          new String[] {
            "/api/appointment/doctors", "/api/appointment/appointments?date=2026-11-10"
          }) {
        mockMvc
            .perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(user)))
            .andExpect(allowed ? status().isOk() : status().isForbidden());
      }
    }
    mockMvc.perform(get("/api/appointment/doctors")).andExpect(status().isUnauthorized());
  }

  @Test
  void invalidFiltersAndMissingDateReturnValidationFailure() throws Exception {
    String token = bearer(seedUser(Set.of(Role.STAFF)));
    for (String suffix :
        new String[] {
          "",
          "?date=bad",
          "?date=2026-11-10&status=DONE",
          "?date=2026-11-10&doctorId=bad",
          "?date=2026-11-10&page=-1",
          "?date=2026-11-10&size=101"
        }) {
      mockMvc
          .perform(
              get("/api/appointment/appointments" + suffix)
                  .header(HttpHeaders.AUTHORIZATION, token))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
  }

  @Test
  void pastAndFutureDayCheckInAreRejectedWhileSameDayLateIsAllowed() throws Exception {
    var staff = seedUser(Set.of(Role.STAFF));
    var doctor = seedUser(Set.of(Role.DOCTOR));
    var pet = seedPet(seedOwner(seedUser(Set.of(Role.OWNER))));
    String token = bearer(staff);
    for (long offset : new long[] {-86400, 86400}) {
      var row =
          seedAppointment(
              pet.getId(),
              doctor.getId(),
              SLOT_START.plusSeconds(offset),
              SLOT_END.plusSeconds(offset));
      mockMvc
          .perform(
              post("/api/appointment/appointments/" + row.getId() + "/check-in")
                  .header(HttpHeaders.AUTHORIZATION, token))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("OPERATION_NOT_ALLOWED"));
      assertThat(appointmentRepository.findById(row.getId()).orElseThrow().getStatus())
          .isEqualTo(AppointmentStatus.SCHEDULED);
    }
    var late =
        seedAppointment(
            pet.getId(),
            doctor.getId(),
            SLOT_START.minusSeconds(3600),
            SLOT_END.minusSeconds(3600));
    mockMvc
        .perform(
            post("/api/appointment/appointments/" + late.getId() + "/check-in")
                .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.checkedInAt").value(SLOT_START.toString()));
  }

  @Test
  void staleQueueNeverCalledAndHistoryRetained() throws Exception {
    var staff = seedUser(Set.of(Role.STAFF));
    var doctor = seedUser(Set.of(Role.DOCTOR));
    var pet = seedPet(seedOwner(seedUser(Set.of(Role.OWNER))));
    var yesterday =
        seedAppointment(
            pet.getId(),
            doctor.getId(),
            SLOT_START.minusSeconds(86400),
            SLOT_END.minusSeconds(86400));
    yesterday.checkIn(SLOT_START.minusSeconds(86400));
    appointmentRepository.saveAndFlush(yesterday);
    String token = bearer(staff);
    mockMvc
        .perform(
            get("/api/appointment/queue")
                .param("doctorId", doctor.getId().toString())
                .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
    mockMvc
        .perform(
            post("/api/appointment/queue/call")
                .param("doctorId", doctor.getId().toString())
                .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isConflict());
    assertThat(appointmentRepository.findById(yesterday.getId()).orElseThrow().getStatus())
        .isEqualTo(AppointmentStatus.CHECKED_IN);
    mockMvc
        .perform(
            get("/api/appointment/appointments")
                .param("date", "2026-11-09")
                .param("doctorId", doctor.getId().toString())
                .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
  }
}
