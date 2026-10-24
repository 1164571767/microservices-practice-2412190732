package com.zjgsu.sfy.pharmacy.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.entity.User;
import com.zjgsu.sfy.pharmacy.LowStockDetected;
import com.zjgsu.sfy.pharmacy.support.PharmacyIntegrationTest;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@RecordApplicationEvents
class InventoryAdjustmentIntegrationTest extends PharmacyIntegrationTest {

  @Autowired ApplicationEvents events;

  private String createDrug(User pharmacist, String name, int threshold, int initialQuantity)
      throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/pharmacy/drugs")
                    .header(HttpHeaders.AUTHORIZATION, bearer(pharmacist))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(drugBody(name, "0.5g", "盒", threshold, initialQuantity)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.id");
  }

  @Test
  void adjustWritesTraceAndUpdatesQuantity() throws Exception {
    User pharmacist = seedPharmacist();
    String drugId = createDrug(pharmacist, uniqueDrugName(), 2, 5);

    mockMvc
        .perform(
            post("/api/pharmacy/inventory/" + drugId + "/adjustments")
                .header(HttpHeaders.AUTHORIZATION, bearer(pharmacist))
                .contentType(MediaType.APPLICATION_JSON)
                .content(adjustBody(3, "盘盈")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.drugId").value(drugId))
        .andExpect(jsonPath("$.delta").value(3))
        .andExpect(jsonPath("$.quantityBefore").value(5))
        .andExpect(jsonPath("$.quantityAfter").value(8))
        .andExpect(jsonPath("$.reason").value("盘盈"))
        .andExpect(jsonPath("$.operatorId").value(pharmacist.getId().toString()));

    mockMvc
        .perform(
            get("/api/pharmacy/inventory/" + drugId)
                .header(HttpHeaders.AUTHORIZATION, bearer(pharmacist)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.quantity").value(8));

    mockMvc
        .perform(
            get("/api/pharmacy/inventory/" + drugId + "/adjustments")
                .header(HttpHeaders.AUTHORIZATION, bearer(pharmacist)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.items[0].delta").value(3))
        .andExpect(jsonPath("$.items[1].reason").value("初始入库"));
  }

  @Test
  void adjustmentBelowZeroIsRejectedAndStockUnchanged() throws Exception {
    User pharmacist = seedPharmacist();
    String drugId = createDrug(pharmacist, uniqueDrugName(), 0, 1);

    mockMvc
        .perform(
            post("/api/pharmacy/inventory/" + drugId + "/adjustments")
                .header(HttpHeaders.AUTHORIZATION, bearer(pharmacist))
                .contentType(MediaType.APPLICATION_JSON)
                .content(adjustBody(-2, "报损")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

    mockMvc
        .perform(
            get("/api/pharmacy/inventory/" + drugId)
                .header(HttpHeaders.AUTHORIZATION, bearer(pharmacist)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.quantity").value(1));
  }

  @Test
  void zeroDeltaIsRejected() throws Exception {
    User pharmacist = seedPharmacist();
    String drugId = createDrug(pharmacist, uniqueDrugName(), 0, 1);

    mockMvc
        .perform(
            post("/api/pharmacy/inventory/" + drugId + "/adjustments")
                .header(HttpHeaders.AUTHORIZATION, bearer(pharmacist))
                .contentType(MediaType.APPLICATION_JSON)
                .content(adjustBody(0, "无效调整")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void ownerCannotAdjustInventory() throws Exception {
    User pharmacist = seedPharmacist();
    String drugId = createDrug(pharmacist, uniqueDrugName(), 0, 1);
    User owner = seedUser(Set.of(Role.OWNER));

    mockMvc
        .perform(
            post("/api/pharmacy/inventory/" + drugId + "/adjustments")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(adjustBody(1, "越权")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
  }

  @Test
  void unknownDrugAdjustmentIsNotFound() throws Exception {
    User pharmacist = seedPharmacist();

    mockMvc
        .perform(
            post("/api/pharmacy/inventory/" + UUID.randomUUID() + "/adjustments")
                .header(HttpHeaders.AUTHORIZATION, bearer(pharmacist))
                .contentType(MediaType.APPLICATION_JSON)
                .content(adjustBody(1, "不存在")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
  }

  @Test
  void lowStockEventFiresOncePerLowPeriodAndRearmsAfterReplenishment() throws Exception {
    User pharmacist = seedPharmacist();
    String name = uniqueDrugName();
    String drugId = createDrug(pharmacist, name, 3, 5);

    adjust(pharmacist, drugId, -3, "消耗");
    assertThat(events.stream(LowStockDetected.class).count()).isEqualTo(1);

    adjust(pharmacist, drugId, -1, "继续消耗");
    assertThat(events.stream(LowStockDetected.class).count()).isEqualTo(1);

    adjust(pharmacist, drugId, 5, "补货");
    assertThat(events.stream(LowStockDetected.class).count()).isEqualTo(1);

    adjust(pharmacist, drugId, -5, "再次消耗");
    assertThat(events.stream(LowStockDetected.class).count()).isEqualTo(2);

    mockMvc
        .perform(
            get("/api/pharmacy/inventory/low-stock")
                .param("query", name)
                .header(HttpHeaders.AUTHORIZATION, bearer(pharmacist)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.items[0].drugId").value(drugId))
        .andExpect(jsonPath("$.items[0].lowStock").value(true));
  }

  @Test
  void lowStockEventIsPublishedWhenDrugStartsBelowThreshold() throws Exception {
    User pharmacist = seedPharmacist();
    createDrug(pharmacist, uniqueDrugName(), 5, 0);

    assertThat(events.stream(LowStockDetected.class).count()).isEqualTo(1);
  }

  private void adjust(User pharmacist, String drugId, int delta, String reason) throws Exception {
    mockMvc
        .perform(
            post("/api/pharmacy/inventory/" + drugId + "/adjustments")
                .header(HttpHeaders.AUTHORIZATION, bearer(pharmacist))
                .contentType(MediaType.APPLICATION_JSON)
                .content(adjustBody(delta, reason)))
        .andExpect(status().isCreated());
  }
}
