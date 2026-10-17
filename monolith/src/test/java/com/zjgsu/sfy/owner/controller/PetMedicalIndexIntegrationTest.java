package com.zjgsu.sfy.owner.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zjgsu.sfy.appointment.entity.Appointment;
import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.entity.User;
import com.zjgsu.sfy.medical.support.MedicalIntegrationTest;
import com.zjgsu.sfy.owner.entity.Pet;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * T-13 联调：owner 经 {@code MedicalApi} 只读聚合拿到真实病历索引（medical 主数据在 M4 落地）。 越权校验仍在 owner 侧完成，owner 不直连
 * medical 表。
 */
class PetMedicalIndexIntegrationTest extends MedicalIntegrationTest {

  @Test
  void ownerSeesOwnPetMedicalRecords() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));
    Pet pet = seedPet(seedOwner(owner));
    User doctor = seedDoctor();
    Appointment appointment = seedAppointment(pet.getId(), doctor.getId());
    Instant visitedAt = Instant.parse("2026-09-15T01:05:00Z");

    mockMvc
        .perform(
            post("/api/medical/records")
                .header(HttpHeaders.AUTHORIZATION, bearer(doctor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(recordBody(appointment.getId(), "咳嗽", "支气管炎", "多休息", visitedAt)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            get("/api/owner/pets/" + pet.getId() + "/medical-records")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].petId").value(pet.getId().toString()))
        .andExpect(jsonPath("$[0].diagnosis").value("支气管炎"))
        .andExpect(jsonPath("$[0].doctorId").value(doctor.getId().toString()));
  }

  @Test
  void staffCanQueryMedicalIndex() throws Exception {
    User staff = seedUser(Set.of(Role.STAFF));
    Pet pet = seedPet(seedOwner(seedUser(Set.of(Role.OWNER))));

    mockMvc
        .perform(
            get("/api/owner/pets/" + pet.getId() + "/medical-records")
                .header(HttpHeaders.AUTHORIZATION, bearer(staff)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void otherOwnerCannotQueryForeignPetMedicalIndex() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));
    seedOwner(owner);
    Pet foreign = seedPet(seedOwner(seedUser(Set.of(Role.OWNER))));

    mockMvc
        .perform(
            get("/api/owner/pets/" + foreign.getId() + "/medical-records")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
  }
}
