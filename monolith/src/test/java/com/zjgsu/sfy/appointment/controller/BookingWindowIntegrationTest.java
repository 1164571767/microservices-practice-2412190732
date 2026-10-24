package com.zjgsu.sfy.appointment.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.zjgsu.sfy.appointment.entity.DoctorSchedule;
import com.zjgsu.sfy.appointment.support.AppointmentIntegrationTest;
import com.zjgsu.sfy.identity.common.Role;
import java.time.*;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

class BookingWindowIntegrationTest extends AppointmentIntegrationTest {
  @Test
  void acceptsTomorrowAndLastDayRejectsPastAndBeyondMonth() throws Exception {
    var staff = seedUser(Set.of(Role.STAFF));
    var doctor = seedUser(Set.of(Role.DOCTOR));
    var pet = seedPet(seedOwner(seedUser(Set.of(Role.OWNER))));
    for (int offset : new int[] {-1, 1, 30, 31}) {
      var start = SLOT_START.plusSeconds(offset * 86400L);
      var end = start.plusSeconds(1800);
      scheduleRepository.saveAndFlush(DoctorSchedule.create(doctor.getId(), start, end, 30));
      var response =
          mockMvc.perform(
              post("/api/appointment/appointments")
                  .header(HttpHeaders.AUTHORIZATION, bearer(staff))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(bookBody(pet.getId(), doctor.getId(), start, end)));
      if (offset == 1 || offset == 30) response.andExpect(status().isCreated());
      else
        response
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
    var existing = seedAppointment(pet.getId(), doctor.getId());
    var start = SLOT_START.plusSeconds(31 * 86400L);
    mockMvc
        .perform(
            post("/api/appointment/appointments/" + existing.getId() + "/reschedule")
                .header(HttpHeaders.AUTHORIZATION, bearer(staff))
                .contentType(MediaType.APPLICATION_JSON)
                .content(rescheduleBody(start, start.plusSeconds(1800))))
        .andExpect(status().isBadRequest());
  }
}
