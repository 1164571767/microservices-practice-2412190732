package com.zjgsu.sfy.medical.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zjgsu.sfy.appointment.entity.Appointment;
import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.entity.User;
import com.zjgsu.sfy.medical.support.MedicalIntegrationTest;
import com.zjgsu.sfy.owner.entity.Pet;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class MedicalRecordIntegrationTest extends MedicalIntegrationTest {

  private static final Instant VISITED_AT = Instant.parse("2026-09-15T01:05:00Z");

  private Pet seedPetWithOwner() {
    return seedPet(seedOwner(seedUser(Set.of(Role.OWNER))));
  }

  @Test
  void attendingDoctorCreatesRecord() throws Exception {
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    Appointment appointment = seedAppointment(pet.getId(), doctor.getId());

    mockMvc
        .perform(
            post("/api/medical/records")
                .header(HttpHeaders.AUTHORIZATION, bearer(doctor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(recordBody(appointment.getId(), "咳嗽", "支气管炎", "多休息", VISITED_AT)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.appointmentId").value(appointment.getId().toString()))
        .andExpect(jsonPath("$.petId").value(pet.getId().toString()))
        .andExpect(jsonPath("$.doctorId").value(doctor.getId().toString()))
        .andExpect(jsonPath("$.diagnosis").value("支气管炎"))
        .andExpect(jsonPath("$.prescriptions.length()").value(0));
  }

  @Test
  void nonAttendingDoctorIsForbidden() throws Exception {
    User attending = seedDoctor();
    User other = seedDoctor();
    Pet pet = seedPetWithOwner();
    Appointment appointment = seedAppointment(pet.getId(), attending.getId());

    mockMvc
        .perform(
            post("/api/medical/records")
                .header(HttpHeaders.AUTHORIZATION, bearer(other))
                .contentType(MediaType.APPLICATION_JSON)
                .content(recordBody(appointment.getId(), "咳嗽", "支气管炎", null, VISITED_AT)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
  }

  @Test
  void ownerCannotCreateRecord() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));
    Pet pet = seedPet(seedOwner(owner));
    User doctor = seedDoctor();
    Appointment appointment = seedAppointment(pet.getId(), doctor.getId());

    mockMvc
        .perform(
            post("/api/medical/records")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(recordBody(appointment.getId(), "咳嗽", "支气管炎", null, VISITED_AT)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
  }

  @Test
  void unknownAppointmentIsNotFound() throws Exception {
    User doctor = seedDoctor();

    mockMvc
        .perform(
            post("/api/medical/records")
                .header(HttpHeaders.AUTHORIZATION, bearer(doctor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(recordBody(UUID.randomUUID(), "咳嗽", "支气管炎", null, VISITED_AT)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
  }

  @Test
  void blankDiagnosisIsRejected() throws Exception {
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    Appointment appointment = seedAppointment(pet.getId(), doctor.getId());

    mockMvc
        .perform(
            post("/api/medical/records")
                .header(HttpHeaders.AUTHORIZATION, bearer(doctor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(recordBody(appointment.getId(), "咳嗽", "", null, VISITED_AT)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void duplicateRecordForSameAppointmentIsRejected() throws Exception {
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    Appointment appointment = seedAppointment(pet.getId(), doctor.getId());
    String body = recordBody(appointment.getId(), "咳嗽", "支气管炎", null, VISITED_AT);

    mockMvc
        .perform(
            post("/api/medical/records")
                .header(HttpHeaders.AUTHORIZATION, bearer(doctor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/medical/records")
                .header(HttpHeaders.AUTHORIZATION, bearer(doctor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("OPERATION_NOT_ALLOWED"));
  }

  @Test
  void staffCannotCreateRecord() throws Exception {
    User staff = seedUser(Set.of(Role.STAFF));
    Pet pet = seedPetWithOwner();
    User doctor = seedDoctor();
    Appointment appointment = seedAppointment(pet.getId(), doctor.getId());

    mockMvc
        .perform(
            post("/api/medical/records")
                .header(HttpHeaders.AUTHORIZATION, bearer(staff))
                .contentType(MediaType.APPLICATION_JSON)
                .content(recordBody(appointment.getId(), "咳嗽", "支气管炎", null, VISITED_AT)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
  }

  @Test
  void futureVisitedAtIsRejected() throws Exception {
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    Appointment appointment = seedAppointment(pet.getId(), doctor.getId());

    mockMvc
        .perform(
            post("/api/medical/records")
                .header(HttpHeaders.AUTHORIZATION, bearer(doctor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    recordBody(
                        appointment.getId(),
                        "咳嗽",
                        "支气管炎",
                        null,
                        Instant.now().plus(Duration.ofDays(1)))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }
}
