package com.zjgsu.sfy.medical.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.entity.User;
import com.zjgsu.sfy.medical.entity.MedicalRecord;
import com.zjgsu.sfy.medical.support.MedicalIntegrationTest;
import com.zjgsu.sfy.owner.entity.Pet;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.Set;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

class MedicalHistoryIntegrationTest extends MedicalIntegrationTest {

  @Autowired private EntityManagerFactory entityManagerFactory;

  private Pet seedPetWithOwner() {
    return seedPet(seedOwner(seedUser(Set.of(Role.OWNER))));
  }

  @Test
  void staffReadsPetHistoryNewestFirstWithPrescriptions() throws Exception {
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    MedicalRecord older =
        seedRecord(pet.getId(), doctor.getId(), Instant.parse("2026-11-10T01:00:00Z"), "支气管炎");
    seedPrescription(older.getId(), doctor.getId(), "阿莫西林");
    seedRecord(pet.getId(), doctor.getId(), Instant.parse("2026-11-11T01:00:00Z"), "皮炎");
    User staff = seedUser(Set.of(Role.STAFF));

    mockMvc
        .perform(
            get("/api/medical/pets/" + pet.getId() + "/records")
                .header(HttpHeaders.AUTHORIZATION, bearer(staff)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.items[0].diagnosis").value("皮炎"))
        .andExpect(jsonPath("$.items[0].prescriptions.length()").value(0))
        .andExpect(jsonPath("$.items[1].diagnosis").value("支气管炎"))
        .andExpect(jsonPath("$.items[1].prescriptions.length()").value(1))
        .andExpect(jsonPath("$.items[1].prescriptions[0].items[0].drugName").value("阿莫西林"));
  }

  @Test
  void historyIsPaginated() throws Exception {
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    for (int i = 1; i <= 3; i++) {
      seedRecord(
          pet.getId(), doctor.getId(), Instant.parse("2026-11-1" + i + "T01:00:00Z"), "诊断" + i);
    }
    User staff = seedUser(Set.of(Role.STAFF));

    mockMvc
        .perform(
            get("/api/medical/pets/" + pet.getId() + "/records?page=0&size=2")
                .header(HttpHeaders.AUTHORIZATION, bearer(staff)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.size").value(2))
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.totalPages").value(2));
  }

  @Test
  void ownerCannotReadClinicalHistory() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));
    Pet pet = seedPet(seedOwner(owner));

    mockMvc
        .perform(
            get("/api/medical/pets/" + pet.getId() + "/records")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
  }

  @Test
  void historyQueryIssuesConstantStatementsRegardlessOfRecordCount() throws Exception {
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    User staff = seedUser(Set.of(Role.STAFF));
    for (int i = 1; i <= 2; i++) {
      MedicalRecord record =
          seedRecord(
              pet.getId(), doctor.getId(), Instant.parse("2026-11-0" + i + "T01:00:00Z"), "诊断" + i);
      seedPrescription(record.getId(), doctor.getId(), "药" + i);
    }
    long withTwoRecords = statementsForHistory(staff, pet);

    for (int i = 3; i <= 6; i++) {
      MedicalRecord record =
          seedRecord(
              pet.getId(), doctor.getId(), Instant.parse("2026-11-0" + i + "T01:00:00Z"), "诊断" + i);
      seedPrescription(record.getId(), doctor.getId(), "药" + i);
    }
    long withSixRecords = statementsForHistory(staff, pet);

    assertThat(withSixRecords).isEqualTo(withTwoRecords);
  }

  private long statementsForHistory(User staff, Pet pet) throws Exception {
    Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    statistics.clear();
    mockMvc
        .perform(
            get("/api/medical/pets/" + pet.getId() + "/records?size=100")
                .header(HttpHeaders.AUTHORIZATION, bearer(staff)))
        .andExpect(status().isOk());
    return statistics.getPrepareStatementCount();
  }
}
