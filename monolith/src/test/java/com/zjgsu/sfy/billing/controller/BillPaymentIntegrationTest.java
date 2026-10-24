package com.zjgsu.sfy.billing.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.zjgsu.sfy.billing.BillPaid;
import com.zjgsu.sfy.billing.BillRefunded;
import com.zjgsu.sfy.billing.common.BillStatus;
import com.zjgsu.sfy.billing.entity.BillOperation;
import com.zjgsu.sfy.billing.entity.ChargeItem;
import com.zjgsu.sfy.billing.entity.PaymentRecord;
import com.zjgsu.sfy.billing.support.BillingIntegrationTest;
import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.entity.User;
import com.zjgsu.sfy.medical.entity.MedicalRecord;
import com.zjgsu.sfy.owner.entity.Pet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@RecordApplicationEvents
class BillPaymentIntegrationTest extends BillingIntegrationTest {

  @Autowired ApplicationEvents events;

  @Test
  void payUnpaidBillCreatesPaymentAndAuditTrail() throws Exception {
    User staff = seedStaff();
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    MedicalRecord record = seedRecordFor(pet, doctor);
    ChargeItem item = seedChargeItem("50.00");
    UUID billId = createBillFor(staff, record, item, 2);

    String response = payBill(staff, billId, null);

    assertThat(JsonPath.<String>read(response, "$.status")).isEqualTo("PAID");
    assertThat(JsonPath.<String>read(response, "$.payment.status")).isEqualTo("SUCCEEDED");
    assertThat(JsonPath.<String>read(response, "$.payment.method")).isEqualTo("MOCK");
    assertThat(JsonPath.<Double>read(response, "$.payment.amount")).isEqualTo(100.00);
    assertThat(JsonPath.<String>read(response, "$.payment.paidBy"))
        .isEqualTo(staff.getId().toString());
    assertThat(billOf(billId).getStatus()).isEqualTo(BillStatus.PAID);
    assertThat(paymentOf(billId).getStatus().name()).isEqualTo("SUCCEEDED");
    assertThat(operationsOf(billId))
        .extracting(BillOperation::getAction)
        .extracting(Enum::name)
        .containsExactly("CREATED", "PAID");
  }

  @Test
  void payingPublishesBillPaidEvent() throws Exception {
    User staff = seedStaff();
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    MedicalRecord record = seedRecordFor(pet, doctor);
    ChargeItem item = seedChargeItem("50.00");
    UUID billId = createBillFor(staff, record, item, 2);

    payBill(staff, billId, null);

    assertThat(events.stream(BillPaid.class)).hasSize(1);
    BillPaid event = events.stream(BillPaid.class).findFirst().orElseThrow();
    assertThat(event.billId()).isEqualTo(billId);
    assertThat(event.petId()).isEqualTo(pet.getId());
    assertThat(event.medicalRecordId()).isEqualTo(record.getId());
    assertThat(event.amount()).isEqualByComparingTo("100.00");
    assertThat(event.currency()).isEqualTo("CNY");
    assertThat(event.paidBy()).isEqualTo(staff.getId());
  }

  @Test
  void repeatPayWithoutKeyReturnsAlreadyPaid() throws Exception {
    User staff = seedStaff();
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    MedicalRecord record = seedRecordFor(pet, doctor);
    ChargeItem item = seedChargeItem("50.00");
    UUID billId = createBillFor(staff, record, item, 1);
    payBill(staff, billId, null);

    mockMvc
        .perform(
            post("/api/billing/bills/" + billId + "/pay")
                .header(HttpHeaders.AUTHORIZATION, bearer(staff)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PAYMENT_ALREADY_PAID"));

    assertThat(paymentRecordRepository.findByBillId(billId)).isPresent();
  }

  @Test
  void repeatPayWithSameKeyReplaysSamePayment() throws Exception {
    User staff = seedStaff();
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    MedicalRecord record = seedRecordFor(pet, doctor);
    ChargeItem item = seedChargeItem("50.00");
    UUID billId = createBillFor(staff, record, item, 1);
    String key = "pay-replay-" + UUID.randomUUID();

    String first = payBill(staff, billId, key);
    String second = payBill(staff, billId, key);

    assertThat(JsonPath.<String>read(second, "$.payment.id"))
        .isEqualTo(JsonPath.<String>read(first, "$.payment.id"));
    assertThat(
            paymentRecordRepository.findAll().stream()
                .filter(payment -> payment.getBillId().equals(billId))
                .count())
        .isEqualTo(1);
  }

  @Test
  void sameKeyForAnotherBillConflicts() throws Exception {
    User staff = seedStaff();
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    MedicalRecord firstRecord = seedRecordFor(pet, doctor);
    MedicalRecord secondRecord = seedRecordFor(pet, doctor);
    ChargeItem item = seedChargeItem("50.00");
    UUID firstBill = createBillFor(staff, firstRecord, item, 1);
    UUID secondBill = createBillFor(staff, secondRecord, item, 1);
    String key = "pay-cross-bill-" + UUID.randomUUID();
    payBill(staff, firstBill, key);

    mockMvc
        .perform(
            post("/api/billing/bills/" + secondBill + "/pay")
                .header(HttpHeaders.AUTHORIZATION, bearer(staff))
                .header("Idempotency-Key", key))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
  }

  @Test
  void voidUnpaidBillThenPayIsRejected() throws Exception {
    User staff = seedStaff();
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    MedicalRecord record = seedRecordFor(pet, doctor);
    ChargeItem item = seedChargeItem("50.00");
    UUID billId = createBillFor(staff, record, item, 1);

    String response = voidBill(staff, billId, "重复开单");
    assertThat(JsonPath.<String>read(response, "$.status")).isEqualTo("VOID");
    assertThat(billOf(billId).getStatus()).isEqualTo(BillStatus.VOID);
    assertThat(operationsOf(billId))
        .extracting(BillOperation::getAction)
        .extracting(Enum::name)
        .containsExactly("CREATED", "VOIDED");

    mockMvc
        .perform(
            post("/api/billing/bills/" + billId + "/pay")
                .header(HttpHeaders.AUTHORIZATION, bearer(staff)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PAYMENT_INVALID_STATE"));
  }

  @Test
  void refundPaidBillRecordsRefundAndRejectsSecondRefund() throws Exception {
    User staff = seedStaff();
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    MedicalRecord record = seedRecordFor(pet, doctor);
    ChargeItem item = seedChargeItem("50.00");
    UUID billId = createBillFor(staff, record, item, 1);
    payBill(staff, billId, null);

    String response = refundBill(staff, billId, "主人取消服务");

    assertThat(JsonPath.<String>read(response, "$.status")).isEqualTo("REFUNDED");
    assertThat(JsonPath.<String>read(response, "$.payment.status")).isEqualTo("REFUNDED");
    assertThat(JsonPath.<String>read(response, "$.payment.refundReason")).isEqualTo("主人取消服务");
    assertThat(JsonPath.<String>read(response, "$.payment.refundedBy"))
        .isEqualTo(staff.getId().toString());
    PaymentRecord payment = paymentOf(billId);
    assertThat(payment.getStatus().name()).isEqualTo("REFUNDED");
    assertThat(payment.getRefundReason()).isEqualTo("主人取消服务");
    assertThat(billOf(billId).getStatus()).isEqualTo(BillStatus.REFUNDED);
    assertThat(operationsOf(billId))
        .extracting(BillOperation::getAction)
        .extracting(Enum::name)
        .containsExactly("CREATED", "PAID", "REFUNDED");
    assertThat(events.stream(BillRefunded.class)).hasSize(1);
    BillRefunded refunded = events.stream(BillRefunded.class).findFirst().orElseThrow();
    assertThat(refunded.billId()).isEqualTo(billId);
    assertThat(refunded.petId()).isEqualTo(pet.getId());
    assertThat(refunded.amount()).isEqualByComparingTo("50.00");
    assertThat(refunded.refundedBy()).isEqualTo(staff.getId());

    mockMvc
        .perform(
            post("/api/billing/bills/" + billId + "/refund")
                .header(HttpHeaders.AUTHORIZATION, bearer(staff))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", "再次退款"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PAYMENT_INVALID_STATE"));
  }

  @Test
  void refundUnpaidBillIsRejected() throws Exception {
    User staff = seedStaff();
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    MedicalRecord record = seedRecordFor(pet, doctor);
    ChargeItem item = seedChargeItem("50.00");
    UUID billId = createBillFor(staff, record, item, 1);

    mockMvc
        .perform(
            post("/api/billing/bills/" + billId + "/refund")
                .header(HttpHeaders.AUTHORIZATION, bearer(staff))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", "未支付"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PAYMENT_INVALID_STATE"));
  }

  @Test
  void voidPaidBillIsRejected() throws Exception {
    User staff = seedStaff();
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    MedicalRecord record = seedRecordFor(pet, doctor);
    ChargeItem item = seedChargeItem("50.00");
    UUID billId = createBillFor(staff, record, item, 1);
    payBill(staff, billId, null);

    mockMvc
        .perform(
            post("/api/billing/bills/" + billId + "/void")
                .header(HttpHeaders.AUTHORIZATION, bearer(staff))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", "已支付不能作废"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PAYMENT_INVALID_STATE"));
  }

  @Test
  void ownerCanReadOwnBillButCannotPayAnyBill() throws Exception {
    User staff = seedStaff();
    User doctor = seedDoctor();
    User ownerOne = seedUser(Set.of(Role.OWNER));
    User ownerTwo = seedUser(Set.of(Role.OWNER));
    Pet petOne = seedPet(seedOwner(ownerOne));
    Pet petTwo = seedPet(seedOwner(ownerTwo));
    MedicalRecord recordOne = seedRecordFor(petOne, doctor);
    MedicalRecord recordTwo = seedRecordFor(petTwo, doctor);
    ChargeItem item = seedChargeItem("50.00");
    UUID billOne = createBillFor(staff, recordOne, item, 1);
    UUID billTwo = createBillFor(staff, recordTwo, item, 1);

    mockMvc
        .perform(
            get("/api/billing/bills/" + billOne)
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerOne)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/billing/bills/" + billOne + "/pay")
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerOne)))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/api/billing/bills/" + billOne + "/pay")
                .header(HttpHeaders.AUTHORIZATION, bearer(doctor)))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            get("/api/billing/bills/" + billTwo)
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerOne)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));

    mockMvc
        .perform(
            post("/api/billing/bills/" + billTwo + "/pay")
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerOne)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
  }

  @Test
  void ownerCannotRefund() throws Exception {
    User staff = seedStaff();
    User doctor = seedDoctor();
    User owner = seedUser(Set.of(Role.OWNER));
    Pet pet = seedPet(seedOwner(owner));
    MedicalRecord record = seedRecordFor(pet, doctor);
    ChargeItem item = seedChargeItem("50.00");
    UUID billId = createBillFor(staff, record, item, 1);
    payBill(staff, billId, null);

    mockMvc
        .perform(
            post("/api/billing/bills/" + billId + "/refund")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", "主人申请退款"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
  }

  @Test
  void listBillsByPetRespectsOwnershipAndStatusFilter() throws Exception {
    User staff = seedStaff();
    User doctor = seedDoctor();
    User owner = seedUser(Set.of(Role.OWNER));
    User otherOwner = seedUser(Set.of(Role.OWNER));
    Pet pet = seedPet(seedOwner(owner));
    seedOwner(otherOwner);
    MedicalRecord recordOne = seedRecordFor(pet, doctor);
    MedicalRecord recordTwo = seedRecordFor(pet, doctor);
    ChargeItem item = seedChargeItem("50.00");
    UUID billOne = createBillFor(staff, recordOne, item, 1);
    createBillFor(staff, recordTwo, item, 2);
    payBill(staff, billOne, null);

    mockMvc
        .perform(
            get("/api/billing/bills")
                .param("petId", pet.getId().toString())
                .header(HttpHeaders.AUTHORIZATION, bearer(staff)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2));

    mockMvc
        .perform(
            get("/api/billing/bills")
                .param("petId", pet.getId().toString())
                .param("status", "PAID")
                .header(HttpHeaders.AUTHORIZATION, bearer(staff)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.items[0].status").value("PAID"));

    mockMvc
        .perform(
            get("/api/billing/bills")
                .param("petId", pet.getId().toString())
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2));

    mockMvc
        .perform(
            get("/api/billing/bills")
                .param("petId", pet.getId().toString())
                .header(HttpHeaders.AUTHORIZATION, bearer(otherOwner)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
  }

  @Test
  void listWithoutPetIdIsRejected() throws Exception {
    User staff = seedStaff();

    mockMvc
        .perform(get("/api/billing/bills").header(HttpHeaders.AUTHORIZATION, bearer(staff)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void unknownBillIsNotFound() throws Exception {
    User staff = seedStaff();
    UUID missing = UUID.randomUUID();

    mockMvc
        .perform(
            get("/api/billing/bills/" + missing).header(HttpHeaders.AUTHORIZATION, bearer(staff)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

    mockMvc
        .perform(
            post("/api/billing/bills/" + missing + "/pay")
                .header(HttpHeaders.AUTHORIZATION, bearer(staff)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
  }

  @Test
  void refundAndVoidRequireReason() throws Exception {
    User staff = seedStaff();
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    MedicalRecord record = seedRecordFor(pet, doctor);
    ChargeItem item = seedChargeItem("50.00");
    UUID billId = createBillFor(staff, record, item, 1);
    String token = bearer(staff);

    mockMvc
        .perform(
            post("/api/billing/bills/" + billId + "/refund")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\" \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    mockMvc
        .perform(
            post("/api/billing/bills/" + billId + "/void")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\" \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  private UUID createBillFor(User staff, MedicalRecord record, ChargeItem item, int quantity)
      throws Exception {
    String response =
        createBill(
            staff, billBody(record.getId(), null, List.of(line(item.getId(), quantity))), null);
    return UUID.fromString(JsonPath.read(response, "$.id"));
  }

  private String refundBill(User operator, UUID billId, String reason) throws Exception {
    return mockMvc
        .perform(
            post("/api/billing/bills/" + billId + "/refund")
                .header(HttpHeaders.AUTHORIZATION, bearer(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", reason))))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private String voidBill(User operator, UUID billId, String reason) throws Exception {
    MockHttpServletRequestBuilder request =
        post("/api/billing/bills/" + billId + "/void")
            .header(HttpHeaders.AUTHORIZATION, bearer(operator))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("reason", reason)));
    return mockMvc
        .perform(request)
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }
}
