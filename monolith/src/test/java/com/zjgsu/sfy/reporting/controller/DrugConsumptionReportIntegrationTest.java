package com.zjgsu.sfy.reporting.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zjgsu.sfy.identity.entity.User;
import com.zjgsu.sfy.medical.entity.Prescription;
import com.zjgsu.sfy.owner.entity.Pet;
import com.zjgsu.sfy.pharmacy.PrescriptionDispensed;
import com.zjgsu.sfy.pharmacy.entity.Drug;
import com.zjgsu.sfy.reporting.dto.resp.DrugConsumptionResponse;
import com.zjgsu.sfy.reporting.repository.DrugConsumptionFactRepository;
import com.zjgsu.sfy.reporting.support.ReportingIntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@RecordApplicationEvents
class DrugConsumptionReportIntegrationTest extends ReportingIntegrationTest {

  @Autowired ApplicationEvents events;
  @Autowired ApplicationEventPublisher eventPublisher;
  @Autowired DrugConsumptionFactRepository drugConsumptionFactRepository;

  @Test
  void dispensingProjectsAndAggregatesDrugConsumption() throws Exception {
    User admin = seedAdmin();
    User pharmacist = seedPharmacist();
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    Drug drug = seedDrug("阿莫西林", DEFAULT_SPEC, 1, 10);
    Prescription first = seedPrescriptionFor(doctor, pet, drug.getName(), DEFAULT_SPEC, 3);
    Prescription second = seedPrescriptionFor(doctor, pet, drug.getName(), DEFAULT_SPEC, 2);

    dispense(pharmacist, first.getId());
    dispense(pharmacist, second.getId());

    DrugConsumptionResponse report = drugConsumptionOf(admin, today(), today(), drug.getId());

    assertThat(report.totalQuantity()).isEqualTo(5);
    assertThat(report.totalDispenses()).isEqualTo(2);
    assertThat(report.items()).hasSize(1);
    DrugConsumptionResponse.DrugConsumptionLine line = report.items().getFirst();
    assertThat(line.drugId()).isEqualTo(drug.getId());
    assertThat(line.drugName()).isEqualTo(drug.getName());
    assertThat(line.specification()).isEqualTo(DEFAULT_SPEC);
    assertThat(line.unit()).isEqualTo("盒");
    assertThat(line.quantity()).isEqualTo(5);
    assertThat(line.dispenseCount()).isEqualTo(2);
    assertThat(events.stream(PrescriptionDispensed.class)).hasSize(2);
  }

  @Test
  void duplicateDispenseEventDoesNotDoubleCount() throws Exception {
    User admin = seedAdmin();
    User pharmacist = seedPharmacist();
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    Drug drug = seedDrug("阿莫西林", DEFAULT_SPEC, 1, 10);
    Prescription prescription = seedPrescriptionFor(doctor, pet, drug.getName(), DEFAULT_SPEC, 3);
    dispense(pharmacist, prescription.getId());
    PrescriptionDispensed event =
        events.stream(PrescriptionDispensed.class).findFirst().orElseThrow();
    LocalDate date = today();
    DrugConsumptionResponse before = drugConsumptionOf(admin, date, date, drug.getId());

    eventPublisher.publishEvent(event);

    DrugConsumptionResponse after = drugConsumptionOf(admin, date, date, drug.getId());
    assertThat(after.totalQuantity()).isEqualTo(before.totalQuantity());
    assertThat(after.totalDispenses()).isEqualTo(before.totalDispenses());
    assertThat(
            drugConsumptionFactRepository.findAll().stream()
                .filter(fact -> fact.getDispenseId().equals(event.dispenseId())))
        .hasSize(1);
  }

  @Test
  void paginationIsClampedAndReported() throws Exception {
    User admin = seedAdmin();

    mockMvc
        .perform(
            get("/api/reporting/drug-consumption?from=1990-01-01&to=1990-01-02&page=-5&size=0")
                .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(1))
        .andExpect(jsonPath("$.totalElements").value(0))
        .andExpect(jsonPath("$.totalPages").value(0))
        .andExpect(jsonPath("$.totalQuantity").value(0));
  }
}
