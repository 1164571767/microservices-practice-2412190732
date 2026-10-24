package com.zjgsu.sfy.pharmacy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.zjgsu.sfy.identity.entity.User;
import com.zjgsu.sfy.medical.entity.Prescription;
import com.zjgsu.sfy.owner.entity.Pet;
import com.zjgsu.sfy.pharmacy.entity.Drug;
import com.zjgsu.sfy.pharmacy.support.PharmacyIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class DispenseConcurrencyTest extends PharmacyIntegrationTest {

  private static final int CONCURRENCY = 6;

  @Test
  void concurrentDispensesDoNotOversell() throws Exception {
    User pharmacist = seedPharmacist();
    String token = bearer(pharmacist);
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    Drug drug = seedDrug("阿莫西林", DEFAULT_SPEC, 0, 5);

    List<UUID> prescriptionIds = new ArrayList<>();
    for (int i = 0; i < CONCURRENCY; i++) {
      prescriptionIds.add(seedPrescriptionFor(doctor, pet, drug.getName(), 1).getId());
    }

    List<Integer> statuses = runConcurrently(token, prescriptionIds);

    assertThat(statuses.stream().filter(status -> status == 201).count()).isEqualTo(5);
    assertThat(statuses.stream().filter(status -> status == 409).count()).isEqualTo(1);
    assertThat(stockOf(drug.getId())).isZero();
    assertThat(dispenseRecordsFor(prescriptionIds)).isEqualTo(5);
  }

  @Test
  void concurrentRetriesOfSamePrescriptionReplayOneDispense() throws Exception {
    User pharmacist = seedPharmacist();
    String token = bearer(pharmacist);
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    Drug drug = seedDrug("阿莫西林", DEFAULT_SPEC, 0, 10);
    Prescription prescription = seedPrescriptionFor(doctor, pet, drug.getName(), 3);

    List<Integer> statuses =
        runConcurrently(token, java.util.Collections.nCopies(CONCURRENCY, prescription.getId()));

    assertThat(statuses).allMatch(status -> status == 201);
    assertThat(stockOf(drug.getId())).isEqualTo(7);
    assertThat(dispenseRecordsFor(List.of(prescription.getId()))).isEqualTo(1);
  }

  private List<Integer> runConcurrently(String token, List<UUID> prescriptionIds) throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY);
    CountDownLatch ready = new CountDownLatch(CONCURRENCY);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<Integer>> futures = new ArrayList<>();
      for (UUID prescriptionId : prescriptionIds) {
        Callable<Integer> task =
            () -> {
              ready.countDown();
              if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent start timed out");
              }
              return mockMvc
                  .perform(
                      post("/api/pharmacy/prescriptions/" + prescriptionId + "/dispense")
                          .header(HttpHeaders.AUTHORIZATION, token))
                  .andReturn()
                  .getResponse()
                  .getStatus();
            };
        futures.add(executor.submit(task));
      }
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();

      List<Integer> statuses = new ArrayList<>();
      for (Future<Integer> future : futures) {
        statuses.add(future.get(30, TimeUnit.SECONDS));
      }
      return statuses;
    } finally {
      executor.shutdownNow();
    }
  }

  private long dispenseRecordsFor(List<UUID> prescriptionIds) {
    Set<UUID> ids = Set.copyOf(prescriptionIds);
    return dispenseRecordRepository.findAll().stream()
        .filter(record -> ids.contains(record.getPrescriptionId()))
        .count();
  }
}
