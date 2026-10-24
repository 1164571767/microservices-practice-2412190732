package com.zjgsu.sfy.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import com.zjgsu.sfy.billing.common.BillStatus;
import com.zjgsu.sfy.billing.entity.ChargeItem;
import com.zjgsu.sfy.billing.support.BillingIntegrationTest;
import com.zjgsu.sfy.identity.entity.User;
import com.zjgsu.sfy.medical.entity.MedicalRecord;
import com.zjgsu.sfy.owner.entity.Pet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class BillConcurrencyTest extends BillingIntegrationTest {

  private static final int CONCURRENCY = 6;

  @Test
  void concurrentDifferentKeysCreateOnlyOneEffectiveBill() throws Exception {
    var staff = seedStaff();
    var doctor = seedDoctor();
    var pet = seedPetWithOwner();
    var record = seedRecordFor(pet, doctor);
    var item = seedChargeItem("15.00");
    var body = billBody(record.getId(), null, List.of(line(item.getId(), 1)));
    List<Callable<Integer>> tasks = new ArrayList<>();
    for (int i = 0; i < CONCURRENCY; i++)
      tasks.add(
          () ->
              mockMvc
                  .perform(
                      post("/api/billing/bills")
                          .header(HttpHeaders.AUTHORIZATION, bearer(staff))
                          .header("Idempotency-Key", UUID.randomUUID().toString())
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(body))
                  .andReturn()
                  .getResponse()
                  .getStatus());
    var statuses = runConcurrently(tasks);
    assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(1);
    assertThat(statuses.stream().filter(s -> s == 409).count()).isEqualTo(CONCURRENCY - 1);
    assertThat(billRepository.countByMedicalRecordId(record.getId())).isEqualTo(1);
  }

  @Test
  void concurrentPaymentsOnlyOneSucceeds() throws Exception {
    User staff = seedStaff();
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    MedicalRecord record = seedRecordFor(pet, doctor);
    ChargeItem item = seedChargeItem("50.00");
    UUID billId = createBillFor(staff, record, item);

    List<Callable<Integer>> tasks = new ArrayList<>();
    for (int i = 0; i < CONCURRENCY; i++) {
      tasks.add(
          () ->
              mockMvc
                  .perform(
                      post("/api/billing/bills/" + billId + "/pay")
                          .header(HttpHeaders.AUTHORIZATION, bearer(staff)))
                  .andReturn()
                  .getResponse()
                  .getStatus());
    }

    List<Integer> statuses = runConcurrently(tasks);

    assertThat(statuses.stream().filter(status -> status == 201).count()).isEqualTo(1);
    assertThat(statuses.stream().filter(status -> status == 409).count()).isEqualTo(5);
    assertThat(billOf(billId).getStatus()).isEqualTo(BillStatus.PAID);
    assertThat(paymentCountFor(billId)).isEqualTo(1);
    assertThat(operationsOf(billId)).hasSize(2);
  }

  @Test
  void concurrentPaymentsWithSameKeyReplayOnePayment() throws Exception {
    User staff = seedStaff();
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    MedicalRecord record = seedRecordFor(pet, doctor);
    ChargeItem item = seedChargeItem("50.00");
    UUID billId = createBillFor(staff, record, item);
    String key = "pay-concurrent-" + UUID.randomUUID();

    List<Callable<Integer>> tasks = new ArrayList<>();
    for (int i = 0; i < CONCURRENCY; i++) {
      tasks.add(
          () ->
              mockMvc
                  .perform(
                      post("/api/billing/bills/" + billId + "/pay")
                          .header(HttpHeaders.AUTHORIZATION, bearer(staff))
                          .header("Idempotency-Key", key))
                  .andReturn()
                  .getResponse()
                  .getStatus());
    }

    List<Integer> statuses = runConcurrently(tasks);

    assertThat(statuses).allMatch(status -> status == 201);
    assertThat(billOf(billId).getStatus()).isEqualTo(BillStatus.PAID);
    assertThat(paymentCountFor(billId)).isEqualTo(1);
  }

  @Test
  void concurrentRefundsOnlyOneSucceeds() throws Exception {
    User staff = seedStaff();
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    MedicalRecord record = seedRecordFor(pet, doctor);
    ChargeItem item = seedChargeItem("50.00");
    UUID billId = createBillFor(staff, record, item);
    payBill(staff, billId, null);

    List<Callable<Integer>> tasks = new ArrayList<>();
    for (int i = 0; i < CONCURRENCY; i++) {
      tasks.add(
          () ->
              mockMvc
                  .perform(
                      post("/api/billing/bills/" + billId + "/refund")
                          .header(HttpHeaders.AUTHORIZATION, bearer(staff))
                          .contentType(MediaType.APPLICATION_JSON)
                          .content("{\"reason\":\"并发退款\"}"))
                  .andReturn()
                  .getResponse()
                  .getStatus());
    }

    List<Integer> statuses = runConcurrently(tasks);

    assertThat(statuses.stream().filter(status -> status == 200).count()).isEqualTo(1);
    assertThat(statuses.stream().filter(status -> status == 409).count()).isEqualTo(5);
    assertThat(billOf(billId).getStatus()).isEqualTo(BillStatus.REFUNDED);
    assertThat(operationsOf(billId)).hasSize(3);
  }

  @Test
  void concurrentBillCreationsWithSameKeyCreateOneBill() throws Exception {
    User staff = seedStaff();
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    MedicalRecord record = seedRecordFor(pet, doctor);
    ChargeItem item = seedChargeItem("50.00");
    String body = billBody(record.getId(), null, List.of(line(item.getId(), 1)));
    String key = "create-concurrent-" + UUID.randomUUID();

    List<Callable<Integer>> tasks = new ArrayList<>();
    for (int i = 0; i < CONCURRENCY; i++) {
      tasks.add(
          () ->
              mockMvc
                  .perform(
                      post("/api/billing/bills")
                          .header(HttpHeaders.AUTHORIZATION, bearer(staff))
                          .header("Idempotency-Key", key)
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(body))
                  .andReturn()
                  .getResponse()
                  .getStatus());
    }

    List<Integer> statuses = runConcurrently(tasks);

    assertThat(statuses).allMatch(status -> status == 201);
    assertThat(billRepository.countByMedicalRecordId(record.getId())).isEqualTo(1);
  }

  private UUID createBillFor(User staff, MedicalRecord record, ChargeItem item) throws Exception {
    String response =
        createBill(staff, billBody(record.getId(), null, List.of(line(item.getId(), 1))), null);
    return UUID.fromString(JsonPath.read(response, "$.id"));
  }

  private long paymentCountFor(UUID billId) {
    return paymentRecordRepository.findAll().stream()
        .filter(payment -> payment.getBillId().equals(billId))
        .count();
  }

  private List<Integer> runConcurrently(List<Callable<Integer>> tasks) throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
    CountDownLatch ready = new CountDownLatch(tasks.size());
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<Integer>> futures = new ArrayList<>();
      for (Callable<Integer> task : tasks) {
        futures.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("concurrent start timed out");
                  }
                  return task.call();
                }));
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
}
