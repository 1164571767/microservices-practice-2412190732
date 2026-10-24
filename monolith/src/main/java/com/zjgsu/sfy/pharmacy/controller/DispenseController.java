package com.zjgsu.sfy.pharmacy.controller;

import com.zjgsu.sfy.identity.IdentityApi;
import com.zjgsu.sfy.pharmacy.dto.resp.DispenseResponse;
import com.zjgsu.sfy.pharmacy.service.DispenseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pharmacy/prescriptions")
@PreAuthorize("hasAnyRole('PHARMACY','ADMIN')")
@Tag(name = "发药", description = "药房按医生处方扣减库存并发药；仅药房和管理员可操作")
public class DispenseController {

  private final DispenseService dispenseService;
  private final IdentityApi identityApi;

  public DispenseController(DispenseService dispenseService, IdentityApi identityApi) {
    this.dispenseService = dispenseService;
    this.identityApi = identityApi;
  }

  @PostMapping("/{prescriptionId}/dispense")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "按处方发药",
      description = "仅药房或管理员。按处方明细匹配药品目录并扣减库存；同一处方重复调用会回放第一次的发药记录，不会重复扣库存。任一药品库存不足时整单失败。")
  public DispenseResponse dispense(
      @Parameter(description = "处方 ID", example = "2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e")
          @PathVariable
          UUID prescriptionId,
      @Parameter(description = "幂等键，可选。同一个键重复提交同一请求会返回第一次的发药记录", example = "dispense-2026-0001")
          @RequestHeader(value = "Idempotency-Key", required = false)
          String idempotencyKey) {
    return DispenseResponse.from(
        dispenseService.dispense(identityApi.currentUser(), prescriptionId, idempotencyKey));
  }
}
