package com.zjgsu.sfy.appointment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.zjgsu.sfy.appointment.support.AppointmentIntegrationTest;
import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.entity.User;
import com.zjgsu.sfy.owner.entity.Owner;
import com.zjgsu.sfy.owner.entity.Pet;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class AppointmentBookingIntegrationTest extends AppointmentIntegrationTest {

  private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

  @Test
  void ownerBooksAppointment() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));
    Pet pet = seedPet(seedOwner(owner));
    User doctor = seedUser(Set.of(Role.DOCTOR));
    seedSchedule(doctor.getId());

    mockMvc
        .perform(
            post("/api/appointment/appointments")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookBody(pet.getId(), doctor.getId(), SLOT_START, SLOT_END)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.petId").value(pet.getId().toString()))
        .andExpect(jsonPath("$.status").value("SCHEDULED"));
  }

  @Test
  void bookingOutsideScheduleIsNotAvailable() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));
    Pet pet = seedPet(seedOwner(owner));
    User doctor = seedUser(Set.of(Role.DOCTOR));
    seedSchedule(doctor.getId());

    mockMvc
        .perform(
            post("/api/appointment/appointments")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    bookBody(
                        pet.getId(),
                        doctor.getId(),
                        Instant.parse("2026-11-10T10:00:00Z"),
                        Instant.parse("2026-11-10T10:30:00Z"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("APPOINTMENT_NOT_AVAILABLE"));
  }

  @Test
  void doubleBookingSameSlotConflicts() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));
    Pet pet = seedPet(seedOwner(owner));
    User doctor = seedUser(Set.of(Role.DOCTOR));
    seedSchedule(doctor.getId());
    seedAppointment(pet.getId(), doctor.getId());

    mockMvc
        .perform(
            post("/api/appointment/appointments")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookBody(pet.getId(), doctor.getId(), SLOT_START, SLOT_END)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("APPOINTMENT_CONFLICT"));
  }

  @Test
  void idempotentRetryReturnsSameAppointment() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));
    Pet pet = seedPet(seedOwner(owner));
    User doctor = seedUser(Set.of(Role.DOCTOR));
    seedSchedule(doctor.getId());
    String key = "book-" + UUID.randomUUID();
    String body = bookBody(pet.getId(), doctor.getId(), SLOT_START, SLOT_END);

    String first =
        mockMvc
            .perform(
                post("/api/appointment/appointments")
                    .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                    .header(IDEMPOTENCY_HEADER, key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String second =
        mockMvc
            .perform(
                post("/api/appointment/appointments")
                    .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                    .header(IDEMPOTENCY_HEADER, key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String firstId = JsonPath.read(first, "$.id");
    String secondId = JsonPath.read(second, "$.id");
    assertThat(secondId).isEqualTo(firstId);
  }

  @Test
  void idempotencyKeyWithDifferentRequestConflicts() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));
    Owner profile = seedOwner(owner);
    Pet petA = seedPet(profile);
    Pet petB = seedPet(profile);
    User doctor = seedUser(Set.of(Role.DOCTOR));
    seedSchedule(doctor.getId());
    String key = "book-" + UUID.randomUUID();

    mockMvc
        .perform(
            post("/api/appointment/appointments")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .header(IDEMPOTENCY_HEADER, key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookBody(petA.getId(), doctor.getId(), SLOT_START, SLOT_END)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/appointment/appointments")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .header(IDEMPOTENCY_HEADER, key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookBody(petB.getId(), doctor.getId(), SLOT_START, SLOT_END)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
  }

  @Test
  void ownerCannotBookForAnotherOwnersPet() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));
    seedOwner(owner);
    Pet foreign = seedPet(seedOwner(seedUser(Set.of(Role.OWNER))));
    User doctor = seedUser(Set.of(Role.DOCTOR));
    seedSchedule(doctor.getId());

    mockMvc
        .perform(
            post("/api/appointment/appointments")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookBody(foreign.getId(), doctor.getId(), SLOT_START, SLOT_END)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
  }
}
