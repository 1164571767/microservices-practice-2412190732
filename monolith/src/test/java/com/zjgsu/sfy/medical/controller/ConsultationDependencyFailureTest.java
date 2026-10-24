package com.zjgsu.sfy.medical.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.medical.ConsultationHandoffLookup;
import com.zjgsu.sfy.medical.repository.ConsultationRepository;
import com.zjgsu.sfy.medical.service.PrescriptionService;
import com.zjgsu.sfy.medical.support.MedicalIntegrationTest;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class ConsultationDependencyFailureTest extends MedicalIntegrationTest {
  @MockitoSpyBean PrescriptionService prescriptionWriter;
  @MockitoSpyBean ConsultationHandoffLookup.Billing billing;
  @MockitoSpyBean ConsultationHandoffLookup.Pharmacy pharmacy;
  @MockitoSpyBean ConsultationRepository consultations;
  @MockitoSpyBean com.zjgsu.sfy.appointment.service.AppointmentLookupAdapter appointments;
  @Autowired com.zjgsu.sfy.pharmacy.repository.PrescriptionTaskRepository tasks;

  private record Fixture(UUID id, String url, String auth) {}

  private Fixture fixture() throws Exception {
    var doctor = seedDoctor();
    var pet = seedPet(seedOwner(seedUser(Set.of(Role.OWNER))));
    var a = seedAppointment(pet.getId(), doctor.getId());
    var f = new Fixture(a.getId(), "/api/medical/consultations/" + a.getId(), bearer(doctor));
    mockMvc
        .perform(
            put(f.url + "/draft")
                .header("Authorization", f.auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "version",
                            0,
                            "draft",
                            Map.of(
                                "symptoms",
                                "症状",
                                "diagnosis",
                                "诊断",
                                "advice",
                                "医嘱",
                                "prescriptionChoice",
                                "PRESCRIPTION",
                                "items",
                                List.of(line("测试药品", 2, 3)))))))
        .andExpect(status().isOk());
    return f;
  }

  private void submit(Fixture f, int status) throws Exception {
    mockMvc
        .perform(
            post(f.url + "/submit")
                .header("Authorization", f.auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":1}"))
        .andExpect(status().is(status));
  }

  @Test
  void prescriptionFailureRollsBackRecordAndPreservesRecoverableDraft() throws Exception {
    var f = fixture();
    doThrow(new IllegalStateException("injected prescription dependency failure"))
        .when(prescriptionWriter)
        .issue(any(), any(), any());
    submit(f, 500);
    assertThat(medicalRecordRepository.findByAppointmentId(f.id)).isEmpty();
    assertThat(consultations.get(f.id).version()).isEqualTo(1);
    assertThat(consultations.get(f.id).draft()).isNotNull();
    assertThat(consultations.operations(f.id, 0)).hasSize(1);
    reset(prescriptionWriter);
    submit(f, 200);
  }

  @Test
  void auditStorageFailureRollsBackFormalSubmissionIncludingPharmacyTask() throws Exception {
    var f = fixture();
    doThrow(
            new org.springframework.dao.DataAccessResourceFailureException(
                "injected audit storage failure"))
        .when(consultations)
        .save(eq(f.id), any(), any(), eq("SUBMITTED"));
    submit(f, 500);
    assertThat(medicalRecordRepository.findByAppointmentId(f.id)).isEmpty();
    assertThat(consultations.get(f.id).version()).isEqualTo(1);
    assertThat(consultations.operations(f.id, 0)).hasSize(1);
    reset(consultations);
    submit(f, 200);
    var record = medicalRecordRepository.findByAppointmentId(f.id).orElseThrow();
    var rx = prescriptionRepository.findByMedicalRecordIdIn(List.of(record.getId())).getFirst();
    assertThat(tasks.existsById(rx.getId())).isTrue();
  }

  @Test
  void billingAndPharmacyFailureNeverMasqueradeAsNoBillOrNoDispenseAndDoNotBlockCompletion()
      throws Exception {
    var f = fixture();
    submit(f, 200);
    doThrow(new IllegalStateException("billing unavailable")).when(billing).findBill(any());
    mockMvc
        .perform(get(f.url + "/handoff").header("Authorization", f.auth))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    mockMvc
        .perform(get(f.url).header("Authorization", f.auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.record").isNotEmpty());
    mockMvc
        .perform(
            post(f.url + "/complete")
                .header("Authorization", f.auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":2,\"noPrescription\":false}"))
        .andExpect(status().isOk());
    reset(billing);
    doThrow(new IllegalStateException("pharmacy unavailable")).when(pharmacy).findDispense(any());
    mockMvc
        .perform(get(f.url + "/handoff").header("Authorization", f.auth))
        .andExpect(status().isInternalServerError());
    reset(pharmacy);
    mockMvc
        .perform(get(f.url + "/handoff").header("Authorization", f.auth))
        .andExpect(status().isOk());
  }

  @Test
  void upstreamAppointmentFailureReturnsErrorAndCannotPersistDraft() throws Exception {
    var f = fixture();
    doThrow(new IllegalStateException("appointment unavailable"))
        .when(appointments)
        .lockById(eq(f.id));
    mockMvc
        .perform(
            put(f.url + "/draft")
                .header("Authorization", f.auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("version", 1, "draft", consultations.get(f.id).draft()))))
        .andExpect(status().isInternalServerError());
    assertThat(consultations.get(f.id).version()).isEqualTo(1);
  }
}
