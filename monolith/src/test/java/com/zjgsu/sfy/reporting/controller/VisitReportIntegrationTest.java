package com.zjgsu.sfy.reporting.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.entity.User;
import com.zjgsu.sfy.medical.MedicalRecordCreated;
import com.zjgsu.sfy.owner.entity.Pet;
import com.zjgsu.sfy.reporting.dto.resp.VisitReportResponse;
import com.zjgsu.sfy.reporting.repository.VisitFactRepository;
import com.zjgsu.sfy.reporting.support.ReportingIntegrationTest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@RecordApplicationEvents
class VisitReportIntegrationTest extends ReportingIntegrationTest {

  private static final LocalDate VISIT_DATE = LocalDate.of(2026, 9, 15);
  private static final Instant VISITED_AT = Instant.parse("2026-09-15T01:05:00Z");

  @Autowired ApplicationEvents events;
  @Autowired ApplicationEventPublisher eventPublisher;
  @Autowired VisitFactRepository visitFactRepository;

  @Test
  void visitReportCountsVisitsAndDoctorWorkload() throws Exception {
    User admin = seedAdmin();
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    createRecord(doctor, pet, Instant.parse("2026-11-10T01:00:00Z"));
    createRecord(doctor, pet, Instant.parse("2026-11-10T02:00:00Z"));

    VisitReportResponse report = visitsOf(admin, VISIT_DATE, VISIT_DATE, doctor.getId());

    assertThat(report.from()).isEqualTo(VISIT_DATE);
    assertThat(report.to()).isEqualTo(VISIT_DATE);
    assertThat(report.totalVisits()).isEqualTo(2);
    assertThat(report.daily()).hasSize(1);
    assertThat(report.daily().getFirst().date()).isEqualTo(VISIT_DATE);
    assertThat(report.daily().getFirst().visits()).isEqualTo(2);
    assertThat(report.doctors()).hasSize(1);
    assertThat(report.doctors().getFirst().doctorId()).isEqualTo(doctor.getId());
    assertThat(report.doctors().getFirst().doctorName()).isEqualTo("测试用户");
    assertThat(report.doctors().getFirst().visits()).isEqualTo(2);
    assertThat(events.stream(MedicalRecordCreated.class)).hasSize(2);
  }

  @Test
  void doctorFilterNarrowsVisitsAndWorkload() throws Exception {
    User admin = seedAdmin();
    User firstDoctor = seedDoctor();
    User secondDoctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    createRecord(firstDoctor, pet, Instant.parse("2026-11-10T01:00:00Z"));
    createRecord(secondDoctor, pet, Instant.parse("2026-11-10T02:00:00Z"));

    VisitReportResponse first = visitsOf(admin, VISIT_DATE, VISIT_DATE, firstDoctor.getId());

    assertThat(first.totalVisits()).isEqualTo(1);
    assertThat(first.doctors()).hasSize(1);
    assertThat(first.doctors().getFirst().doctorId()).isEqualTo(firstDoctor.getId());
  }

  @Test
  void duplicateMedicalRecordEventIsIgnored() throws Exception {
    User admin = seedAdmin();
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    createRecord(doctor, pet, Instant.parse("2026-11-10T01:00:00Z"));
    MedicalRecordCreated event =
        events.stream(MedicalRecordCreated.class).findFirst().orElseThrow();
    LocalDate date = VISIT_DATE;

    eventPublisher.publishEvent(event);

    assertThat(
            visitFactRepository.findAll().stream()
                .filter(fact -> fact.getRecordId().equals(event.recordId())))
        .hasSize(1);
    assertThat(visitsOf(admin, date, date, doctor.getId()).totalVisits()).isEqualTo(1);
  }

  @Test
  void nonAdminCannotReadVisitReport() throws Exception {
    User staff = seedUser(Set.of(Role.STAFF));
    User owner = seedUser(Set.of(Role.OWNER));

    mockMvc
        .perform(
            get("/api/reporting/visits?from=2026-09-15&to=2026-09-15")
                .header(HttpHeaders.AUTHORIZATION, bearer(staff)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));

    mockMvc
        .perform(
            get("/api/reporting/visits?from=2026-09-15&to=2026-09-15")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
  }

  private void createRecord(User doctor, Pet pet, Instant appointmentStart) throws Exception {
    UUID appointmentId =
        seedAppointment(
                pet.getId(), doctor.getId(), appointmentStart, appointmentStart.plusSeconds(1800))
            .getId();
    mockMvc
        .perform(
            post("/api/medical/records")
                .header(HttpHeaders.AUTHORIZATION, bearer(doctor))
                .contentType(JSON)
                .content(recordBody(appointmentId, "咳嗽", "支气管炎", "多休息", VISITED_AT)))
        .andExpect(status().isCreated());
  }
}
