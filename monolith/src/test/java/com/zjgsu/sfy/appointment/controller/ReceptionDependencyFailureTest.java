package com.zjgsu.sfy.appointment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.zjgsu.sfy.appointment.support.AppointmentIntegrationTest;
import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.owner.OwnerApi;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class ReceptionDependencyFailureTest extends AppointmentIntegrationTest {
  @MockitoSpyBean OwnerApi ownerApi;

  @Test
  void archiveReadFailureIsAnErrorNotAnEmptyReceptionList() throws Exception {
    var staff = seedUser(Set.of(Role.STAFF));
    var doctor = seedUser(Set.of(Role.DOCTOR));
    var pet = seedPet(seedOwner(seedUser(Set.of(Role.OWNER))));
    var row = seedAppointment(pet.getId(), doctor.getId());
    OwnerApi target = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(ownerApi);
    doThrow(new IllegalStateException("injected archive dependency failure"))
        .when(target)
        .patientContacts(anyCollection());
    mockMvc
        .perform(
            get("/api/appointment/appointments")
                .param("date", "2026-11-10")
                .param("doctorId", doctor.getId().toString())
                .header(HttpHeaders.AUTHORIZATION, bearer(staff)))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
        .andExpect(jsonPath("$.items").doesNotExist())
        .andExpect(jsonPath("$.stackTrace").doesNotExist());
    assertThat(appointmentRepository.findById(row.getId())).isPresent();
  }

  @Test
  void archiveWriteDependencyFailureDoesNotCreateAppointment() throws Exception {
    var staff = seedUser(Set.of(Role.STAFF));
    var doctor = seedUser(Set.of(Role.DOCTOR));
    var pet = seedPet(seedOwner(seedUser(Set.of(Role.OWNER))));
    seedSchedule(doctor.getId());
    OwnerApi target = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(ownerApi);
    doThrow(new IllegalStateException("injected active archive failure"))
        .when(target)
        .requireActivePetForBooking(pet.getId());
    mockMvc
        .perform(
            post("/api/appointment/appointments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookBody(pet.getId(), doctor.getId(), SLOT_START, SLOT_END))
                .header(HttpHeaders.AUTHORIZATION, bearer(staff)))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    assertThat(appointmentRepository.findByPetIdOrderByStartAtDesc(pet.getId())).isEmpty();
  }
}
