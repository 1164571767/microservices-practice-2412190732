package com.zjgsu.sfy.medical.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.zjgsu.sfy.appointment.entity.Appointment;
import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.entity.User;
import com.zjgsu.sfy.medical.support.MedicalIntegrationTest;
import com.zjgsu.sfy.owner.entity.Pet;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class PrescriptionIntegrationTest extends MedicalIntegrationTest {

  private static final Instant VISITED_AT = Instant.parse("2026-09-15T01:05:00Z");

  private String createRecord(User doctor, UUID appointmentId) throws Exception {
    return JsonPath.read(
        mockMvc
            .perform(
                post("/api/medical/records")
                    .header(HttpHeaders.AUTHORIZATION, bearer(doctor))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(recordBody(appointmentId, "咳嗽", "支气管炎", null, VISITED_AT)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString(),
        "$.id");
  }

  private String issue(User doctor, String recordId, String body) throws Exception {
    return mockMvc
        .perform(
            post("/api/medical/records/" + recordId + "/prescriptions")
                .header(HttpHeaders.AUTHORIZATION, bearer(doctor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  @Test
  void attendingDoctorIssuesPrescription() throws Exception {
    User doctor = seedDoctor();
    Pet pet = seedPet(seedOwner(seedUser(Set.of(Role.OWNER))));
    Appointment appointment = seedAppointment(pet.getId(), doctor.getId());
    String recordId = createRecord(doctor, appointment.getId());

    mockMvc
        .perform(
            post("/api/medical/records/" + recordId + "/prescriptions")
                .header(HttpHeaders.AUTHORIZATION, bearer(doctor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(prescriptionBody(List.of(line("阿莫西林", 3, 5)))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.medicalRecordId").value(recordId))
        .andExpect(jsonPath("$.doctorId").value(doctor.getId().toString()))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].drugName").value("阿莫西林"))
        .andExpect(jsonPath("$.items[0].quantity").value(3));
  }

  @Test
  void nonAttendingDoctorIsForbidden() throws Exception {
    User attending = seedDoctor();
    User other = seedDoctor();
    Pet pet = seedPet(seedOwner(seedUser(Set.of(Role.OWNER))));
    Appointment appointment = seedAppointment(pet.getId(), attending.getId());
    String recordId = createRecord(attending, appointment.getId());

    mockMvc
        .perform(
            post("/api/medical/records/" + recordId + "/prescriptions")
                .header(HttpHeaders.AUTHORIZATION, bearer(other))
                .contentType(MediaType.APPLICATION_JSON)
                .content(prescriptionBody(List.of(line("阿莫西林", 3, 5)))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
  }

  @Test
  void duplicatePrescriptionForSameRecordIsRejected() throws Exception {
    User doctor = seedDoctor();
    Pet pet = seedPet(seedOwner(seedUser(Set.of(Role.OWNER))));
    Appointment appointment = seedAppointment(pet.getId(), doctor.getId());
    String recordId = createRecord(doctor, appointment.getId());
    String body = prescriptionBody(List.of(line("阿莫西林", 3, 5)));

    issue(doctor, recordId, body);

    mockMvc
        .perform(
            post("/api/medical/records/" + recordId + "/prescriptions")
                .header(HttpHeaders.AUTHORIZATION, bearer(doctor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("OPERATION_NOT_ALLOWED"));
  }

  @Test
  void unknownRecordIsNotFound() throws Exception {
    User doctor = seedDoctor();

    mockMvc
        .perform(
            post("/api/medical/records/" + UUID.randomUUID() + "/prescriptions")
                .header(HttpHeaders.AUTHORIZATION, bearer(doctor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(prescriptionBody(List.of(line("阿莫西林", 3, 5)))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
  }

  @Test
  void emptyItemsIsRejected() throws Exception {
    User doctor = seedDoctor();
    Pet pet = seedPet(seedOwner(seedUser(Set.of(Role.OWNER))));
    Appointment appointment = seedAppointment(pet.getId(), doctor.getId());
    String recordId = createRecord(doctor, appointment.getId());

    mockMvc
        .perform(
            post("/api/medical/records/" + recordId + "/prescriptions")
                .header(HttpHeaders.AUTHORIZATION, bearer(doctor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(prescriptionBody(List.of())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void nonPositiveQuantityIsRejected() throws Exception {
    User doctor = seedDoctor();
    Pet pet = seedPet(seedOwner(seedUser(Set.of(Role.OWNER))));
    Appointment appointment = seedAppointment(pet.getId(), doctor.getId());
    String recordId = createRecord(doctor, appointment.getId());

    mockMvc
        .perform(
            post("/api/medical/records/" + recordId + "/prescriptions")
                .header(HttpHeaders.AUTHORIZATION, bearer(doctor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(prescriptionBody(List.of(line("阿莫西林", 0, 5)))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }
}
