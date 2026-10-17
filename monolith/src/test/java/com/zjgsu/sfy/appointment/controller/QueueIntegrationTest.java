package com.zjgsu.sfy.appointment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zjgsu.sfy.appointment.QueueNumberCalled;
import com.zjgsu.sfy.appointment.entity.Appointment;
import com.zjgsu.sfy.appointment.support.AppointmentIntegrationTest;
import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.entity.User;
import com.zjgsu.sfy.owner.entity.Pet;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@RecordApplicationEvents
class QueueIntegrationTest extends AppointmentIntegrationTest {

  @Autowired ApplicationEvents events;

  @Test
  void staffSeesCheckedInAppointmentsInQueue() throws Exception {
    User staff = seedUser(Set.of(Role.STAFF));
    User doctor = seedUser(Set.of(Role.DOCTOR));
    Pet pet = seedPet(seedOwner(seedUser(Set.of(Role.OWNER))));
    Appointment first = seedAppointment(pet.getId(), doctor.getId());
    Appointment second =
        seedAppointment(
            pet.getId(),
            doctor.getId(),
            Instant.parse("2026-11-10T01:30:00Z"),
            Instant.parse("2026-11-10T02:00:00Z"));
    String token = bearer(staff);

    mockMvc
        .perform(
            post("/api/appointment/appointments/" + first.getId() + "/check-in")
                .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/appointment/appointments/" + second.getId() + "/check-in")
                .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/appointment/queue")
                .param("doctorId", doctor.getId().toString())
                .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].position").value(1))
        .andExpect(jsonPath("$[1].position").value(2));
  }

  @Test
  void callNextTransitionsAndPublishesQueueNumberCalled() throws Exception {
    User staff = seedUser(Set.of(Role.STAFF));
    User doctor = seedUser(Set.of(Role.DOCTOR));
    Pet pet = seedPet(seedOwner(seedUser(Set.of(Role.OWNER))));
    Appointment appointment = seedAppointment(pet.getId(), doctor.getId());
    String token = bearer(staff);
    mockMvc
        .perform(
            post("/api/appointment/appointments/" + appointment.getId() + "/check-in")
                .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/appointment/queue/call")
                .param("doctorId", doctor.getId().toString())
                .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(appointment.getId().toString()))
        .andExpect(jsonPath("$.status").value("CALLED"));

    assertThat(events.stream(QueueNumberCalled.class).count()).isEqualTo(1);

    mockMvc
        .perform(
            get("/api/appointment/queue")
                .param("doctorId", doctor.getId().toString())
                .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void callingWithEmptyQueueIsRejected() throws Exception {
    User staff = seedUser(Set.of(Role.STAFF));
    User doctor = seedUser(Set.of(Role.DOCTOR));

    mockMvc
        .perform(
            post("/api/appointment/queue/call")
                .param("doctorId", doctor.getId().toString())
                .header(HttpHeaders.AUTHORIZATION, bearer(staff)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("OPERATION_NOT_ALLOWED"));
  }

  @Test
  void ownerCannotViewQueue() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));
    User doctor = seedUser(Set.of(Role.DOCTOR));

    mockMvc
        .perform(
            get("/api/appointment/queue")
                .param("doctorId", doctor.getId().toString())
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
  }
}
