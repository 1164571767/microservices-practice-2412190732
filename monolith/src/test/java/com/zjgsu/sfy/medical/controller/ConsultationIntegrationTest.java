package com.zjgsu.sfy.medical.controller;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.zjgsu.sfy.appointment.entity.Appointment;
import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.entity.User;
import com.zjgsu.sfy.medical.repository.ConsultationRepository;
import com.zjgsu.sfy.medical.support.MedicalIntegrationTest;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class ConsultationIntegrationTest extends MedicalIntegrationTest {
  @Autowired ConsultationRepository consultations;
  private static final MediaType JSON = MediaType.APPLICATION_JSON;

  private record Fixture(User doctor, Appointment appointment, String url, String auth) {}

  private Fixture fixture() throws Exception {
    var doctor = seedDoctor();
    var pet = seedPet(seedOwner(seedUser(Set.of(Role.OWNER))));
    var a = seedAppointment(pet.getId(), doctor.getId());
    return new Fixture(doctor, a, "/api/medical/consultations/" + a.getId(), bearer(doctor));
  }

  private Map<String, Object> draft(String choice) {
    return Map.of(
        "symptoms",
        "咳嗽",
        "diagnosis",
        "门诊诊断",
        "advice",
        "观察和复诊",
        "prescriptionChoice",
        choice,
        "items",
        choice.equals("PRESCRIPTION") ? List.of(line("测试药品", 3, 5)) : List.of());
  }

  private void save(Fixture f, String choice) throws Exception {
    mockMvc
        .perform(
            put(f.url + "/draft")
                .header("Authorization", f.auth)
                .contentType(JSON)
                .content(
                    objectMapper.writeValueAsString(Map.of("version", 0, "draft", draft(choice)))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1));
  }

  private void submit(Fixture f) throws Exception {
    mockMvc
        .perform(
            post(f.url + "/submit")
                .header("Authorization", f.auth)
                .contentType(JSON)
                .content("{\"version\":1}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2));
  }

  @Test
  void draftRestoresAndAtomicPrescriptionSubmissionCompletes() throws Exception {
    var f = fixture();
    save(f, "PRESCRIPTION");
    mockMvc
        .perform(get(f.url).header("Authorization", f.auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.draft.diagnosis").value("门诊诊断"))
        .andExpect(jsonPath("$.record").doesNotExist());
    submit(f);
    mockMvc
        .perform(get(f.url).header("Authorization", f.auth))
        .andExpect(jsonPath("$.record.prescriptions.length()").value(1))
        .andExpect(jsonPath("$.draft").doesNotExist());
    mockMvc
        .perform(
            post(f.url + "/complete")
                .header("Authorization", f.auth)
                .contentType(JSON)
                .content("{\"version\":2,\"noPrescription\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.completedAt").isNotEmpty())
        .andExpect(jsonPath("$.canWrite").value(false));
    assertThat(consultations.operations(f.appointment.getId(), 0))
        .extracting(ConsultationRepository.Operation::action)
        .containsExactly("COMPLETED", "SUBMITTED", "DRAFT_SAVED");
    assertThat(
            appointmentRepository.findById(f.appointment.getId()).orElseThrow().getStatus().name())
        .isEqualTo("CALLED");
    mockMvc
        .perform(get(f.url + "/handoff").header("Authorization", f.auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.prescriptionId").isNotEmpty())
        .andExpect(jsonPath("$.bill").doesNotExist())
        .andExpect(jsonPath("$.dispense").doesNotExist());
  }

  @Test
  void explicitNoPrescriptionCompletesAndCannotBeOverriddenViaLegacyEndpoint() throws Exception {
    var f = fixture();
    save(f, "NONE");
    submit(f);
    var record = medicalRecordRepository.findByAppointmentId(f.appointment.getId()).orElseThrow();
    mockMvc
        .perform(
            post("/api/medical/records/" + record.getId() + "/prescriptions")
                .header("Authorization", f.auth)
                .contentType(JSON)
                .content(prescriptionBody(List.of(line("药品", 1, 1)))))
        .andExpect(status().isConflict());
    mockMvc
        .perform(
            post(f.url + "/complete")
                .header("Authorization", f.auth)
                .contentType(JSON)
                .content("{\"version\":2,\"noPrescription\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.prescriptionChoice").value("NONE"));
  }

  @Test
  void staleVersionAndDuplicateSubmissionNeverOverwriteOrDuplicate() throws Exception {
    var f = fixture();
    save(f, "NONE");
    mockMvc
        .perform(
            put(f.url + "/draft")
                .header("Authorization", f.auth)
                .contentType(JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("version", 0, "draft", draft("PENDING")))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    submit(f);
    mockMvc
        .perform(
            post(f.url + "/submit")
                .header("Authorization", f.auth)
                .contentType(JSON)
                .content("{\"version\":1}"))
        .andExpect(status().isConflict());
    assertThat(medicalRecordRepository.findByAppointmentId(f.appointment.getId())).isPresent();
    assertThat(consultations.operations(f.appointment.getId(), 0)).hasSize(2);
  }

  @Test
  void incompleteDraftCanSaveButCannotSubmitOrComplete() throws Exception {
    var f = fixture();
    var body =
        Map.of(
            "version",
            0,
            "draft",
            Map.of(
                "symptoms",
                "",
                "diagnosis",
                "",
                "advice",
                "",
                "prescriptionChoice",
                "PENDING",
                "items",
                List.of()));
    mockMvc
        .perform(
            put(f.url + "/draft")
                .header("Authorization", f.auth)
                .contentType(JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post(f.url + "/submit")
                .header("Authorization", f.auth)
                .contentType(JSON)
                .content("{\"version\":1}"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            post(f.url + "/complete")
                .header("Authorization", f.auth)
                .contentType(JSON)
                .content("{\"version\":1,\"noPrescription\":true}"))
        .andExpect(status().isConflict());
    assertThat(medicalRecordRepository.findByAppointmentId(f.appointment.getId())).isEmpty();
  }

  @Test
  void scheduledAndCancelledAppointmentsRejectBothNewAndLegacyWrites() throws Exception {
    var f = fixture();
    for (boolean cancelled : List.of(false, true)) {
      var a =
          Appointment.book(
              f.appointment.getPetId(),
              f.doctor.getId(),
              SLOT_END.plusSeconds(cancelled ? 7200 : 3600),
              SLOT_END.plusSeconds(cancelled ? 9000 : 5400));
      if (cancelled) a.cancel();
      appointmentRepository.saveAndFlush(a);
      mockMvc
          .perform(
              put("/api/medical/consultations/" + a.getId() + "/draft")
                  .header("Authorization", f.auth)
                  .contentType(JSON)
                  .content(
                      objectMapper.writeValueAsString(
                          Map.of("version", 0, "draft", draft("NONE")))))
          .andExpect(status().isConflict());
      mockMvc
          .perform(
              post("/api/medical/records")
                  .header("Authorization", f.auth)
                  .contentType(JSON)
                  .content(recordBody(a.getId(), "症状", "诊断", null, null)))
          .andExpect(status().isConflict());
    }
  }

  @Test
  void nonAttendingAndAdministrativeRolesAreReadOnlyAndCannotReadPrivateDraft() throws Exception {
    var f = fixture();
    save(f, "NONE");
    for (var role : List.of(Role.DOCTOR, Role.STAFF, Role.ADMIN)) {
      String auth = bearer(seedUser(Set.of(role)));
      mockMvc
          .perform(get(f.url).header("Authorization", auth))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.canWrite").value(false))
          .andExpect(jsonPath("$.draft").doesNotExist());
      mockMvc
          .perform(
              put(f.url + "/draft")
                  .header("Authorization", auth)
                  .contentType(JSON)
                  .content(
                      objectMapper.writeValueAsString(
                          Map.of("version", 1, "draft", draft("NONE")))))
          .andExpect(status().isForbidden());
      mockMvc
          .perform(
              post(f.url + "/complete")
                  .header("Authorization", auth)
                  .contentType(JSON)
                  .content("{\"version\":1,\"noPrescription\":true}"))
          .andExpect(status().isForbidden());
    }
  }

  @Test
  void unauthorizedRolesAnonymousAndInvalidParametersAreRejected() throws Exception {
    var f = fixture();
    mockMvc.perform(get(f.url)).andExpect(status().isUnauthorized());
    for (var role : List.of(Role.OWNER, Role.PHARMACY))
      mockMvc
          .perform(get(f.url).header("Authorization", bearer(seedUser(Set.of(role)))))
          .andExpect(status().isForbidden());
    for (String version : List.of("-1", "null"))
      mockMvc
          .perform(
              post(f.url + "/submit")
                  .header("Authorization", f.auth)
                  .contentType(JSON)
                  .content("{\"version\":" + version + "}"))
          .andExpect(status().isBadRequest());
    mockMvc
        .perform(get(f.url + "/operations?page=-1").header("Authorization", f.auth))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            get("/api/medical/consultations/" + UUID.randomUUID()).header("Authorization", f.auth))
        .andExpect(status().isNotFound());
  }

  @Test
  void completedConsultationRejectsFurtherWritesAndCompletion() throws Exception {
    var f = fixture();
    save(f, "NONE");
    submit(f);
    mockMvc
        .perform(
            post(f.url + "/complete")
                .header("Authorization", f.auth)
                .contentType(JSON)
                .content("{\"version\":2,\"noPrescription\":true}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            put(f.url + "/draft")
                .header("Authorization", f.auth)
                .contentType(JSON)
                .content(
                    objectMapper.writeValueAsString(Map.of("version", 3, "draft", draft("NONE")))))
        .andExpect(status().isConflict());
    mockMvc
        .perform(
            post(f.url + "/complete")
                .header("Authorization", f.auth)
                .contentType(JSON)
                .content("{\"version\":3,\"noPrescription\":true}"))
        .andExpect(status().isConflict());
    assertThat(consultations.operations(f.appointment.getId(), 0)).hasSize(3);
  }
}
