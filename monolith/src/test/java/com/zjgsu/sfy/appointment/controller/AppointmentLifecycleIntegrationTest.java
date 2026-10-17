package com.zjgsu.sfy.appointment.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zjgsu.sfy.appointment.entity.Appointment;
import com.zjgsu.sfy.appointment.support.AppointmentIntegrationTest;
import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.entity.User;
import com.zjgsu.sfy.owner.entity.Pet;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class AppointmentLifecycleIntegrationTest extends AppointmentIntegrationTest {

  @Test
  void ownerCancelsScheduledAppointment() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));
    Pet pet = seedPet(seedOwner(owner));
    User doctor = seedUser(Set.of(Role.DOCTOR));
    Appointment appointment = seedAppointment(pet.getId(), doctor.getId());

    mockMvc
        .perform(
            post("/api/appointment/appointments/" + appointment.getId() + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
  }

  @Test
  void cancellingFreesTheSlotForRebooking() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));
    Pet pet = seedPet(seedOwner(owner));
    User doctor = seedUser(Set.of(Role.DOCTOR));
    seedSchedule(doctor.getId());
    Appointment appointment = seedAppointment(pet.getId(), doctor.getId());
    String token = bearer(owner);

    mockMvc
        .perform(
            post("/api/appointment/appointments/" + appointment.getId() + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/appointment/appointments")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookBody(pet.getId(), doctor.getId(), SLOT_START, SLOT_END)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("SCHEDULED"));
  }

  @Test
  void cancellingTwiceIsInvalidState() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));
    Pet pet = seedPet(seedOwner(owner));
    User doctor = seedUser(Set.of(Role.DOCTOR));
    Appointment appointment = seedAppointment(pet.getId(), doctor.getId());
    String token = bearer(owner);

    mockMvc
        .perform(
            post("/api/appointment/appointments/" + appointment.getId() + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/appointment/appointments/" + appointment.getId() + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("APPOINTMENT_INVALID_STATE"));
  }

  @Test
  void ownerReschedulesToANewSlot() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));
    Pet pet = seedPet(seedOwner(owner));
    User doctor = seedUser(Set.of(Role.DOCTOR));
    seedSchedule(doctor.getId());
    Appointment appointment = seedAppointment(pet.getId(), doctor.getId());
    Instant newStart = Instant.parse("2026-11-10T02:00:00Z");
    Instant newEnd = Instant.parse("2026-11-10T02:30:00Z");

    mockMvc
        .perform(
            post("/api/appointment/appointments/" + appointment.getId() + "/reschedule")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(rescheduleBody(newStart, newEnd)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.startAt").value(newStart.toString()))
        .andExpect(jsonPath("$.status").value("SCHEDULED"));
  }

  @Test
  void reschedulingCancelledAppointmentIsInvalidState() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));
    Pet pet = seedPet(seedOwner(owner));
    User doctor = seedUser(Set.of(Role.DOCTOR));
    seedSchedule(doctor.getId());
    Appointment appointment = seedAppointment(pet.getId(), doctor.getId());
    String token = bearer(owner);

    mockMvc
        .perform(
            post("/api/appointment/appointments/" + appointment.getId() + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/appointment/appointments/" + appointment.getId() + "/reschedule")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    rescheduleBody(
                        Instant.parse("2026-11-10T02:00:00Z"),
                        Instant.parse("2026-11-10T02:30:00Z"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("APPOINTMENT_INVALID_STATE"));
  }

  @Test
  void staffChecksInAppointment() throws Exception {
    User staff = seedUser(Set.of(Role.STAFF));
    User doctor = seedUser(Set.of(Role.DOCTOR));
    Pet pet = seedPet(seedOwner(seedUser(Set.of(Role.OWNER))));
    Appointment appointment = seedAppointment(pet.getId(), doctor.getId());

    mockMvc
        .perform(
            post("/api/appointment/appointments/" + appointment.getId() + "/check-in")
                .header(HttpHeaders.AUTHORIZATION, bearer(staff)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CHECKED_IN"));
  }

  @Test
  void ownerCannotCheckIn() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));
    Pet pet = seedPet(seedOwner(owner));
    User doctor = seedUser(Set.of(Role.DOCTOR));
    Appointment appointment = seedAppointment(pet.getId(), doctor.getId());

    mockMvc
        .perform(
            post("/api/appointment/appointments/" + appointment.getId() + "/check-in")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
  }
}
