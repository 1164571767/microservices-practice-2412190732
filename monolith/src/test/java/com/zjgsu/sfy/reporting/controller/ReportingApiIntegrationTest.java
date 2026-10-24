package com.zjgsu.sfy.reporting.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.entity.User;
import com.zjgsu.sfy.reporting.dto.resp.DrugConsumptionResponse;
import com.zjgsu.sfy.reporting.dto.resp.VisitReportResponse;
import com.zjgsu.sfy.reporting.support.ReportingIntegrationTest;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class ReportingApiIntegrationTest extends ReportingIntegrationTest {

  @Test
  void anonymousIsRejected() throws Exception {
    mockMvc
        .perform(get("/api/reporting/revenue?from=2026-01-01&to=2026-01-02"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_UNAUTHENTICATED"));
  }

  @Test
  void nonAdminIsForbiddenOnEveryReport() throws Exception {
    User pharmacist = seedUser(Set.of(Role.PHARMACY));
    String token = bearer(pharmacist);

    for (String path :
        new String[] {
          "/api/reporting/visits?from=2026-01-01&to=2026-01-02",
          "/api/reporting/revenue?from=2026-01-01&to=2026-01-02",
          "/api/reporting/drug-consumption?from=2026-01-01&to=2026-01-02"
        }) {
      mockMvc
          .perform(get(path).header(HttpHeaders.AUTHORIZATION, token))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }
  }

  @Test
  void missingDateIsValidationFailure() throws Exception {
    User admin = seedAdmin();

    mockMvc
        .perform(
            get("/api/reporting/revenue?to=2026-01-02")
                .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void invalidDateIsValidationFailure() throws Exception {
    User admin = seedAdmin();

    mockMvc
        .perform(
            get("/api/reporting/visits?from=2026-13-01&to=2026-12-31")
                .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void reversedOrOversizedRangeIsRejected() throws Exception {
    User admin = seedAdmin();
    String token = bearer(admin);

    mockMvc
        .perform(
            get("/api/reporting/revenue?from=2026-02-01&to=2026-01-01")
                .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    mockMvc
        .perform(
            get("/api/reporting/revenue?from=2020-01-01&to=2021-12-31")
                .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void emptyRangeReturnsEmptyReports() throws Exception {
    User admin = seedAdmin();
    LocalDate from = LocalDate.of(1990, 1, 1);
    LocalDate to = LocalDate.of(1990, 1, 2);

    VisitReportResponse visits = visitsOf(admin, from, to, null);
    assertThat(visits.totalVisits()).isZero();
    assertThat(visits.daily()).isEmpty();
    assertThat(visits.doctors()).isEmpty();

    DrugConsumptionResponse consumption = drugConsumptionOf(admin, from, to, null);
    assertThat(consumption.totalQuantity()).isZero();
    assertThat(consumption.totalDispenses()).isZero();
    assertThat(consumption.items()).isEmpty();
  }
}
