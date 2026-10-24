package com.zjgsu.sfy.pharmacy.controller;

import com.zjgsu.sfy.identity.IdentityApi;
import com.zjgsu.sfy.pharmacy.dto.resp.*;
import com.zjgsu.sfy.pharmacy.service.PharmacyWorkbenchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pharmacy/workbench/prescriptions")
@PreAuthorize("hasAnyRole('PHARMACY','ADMIN')")
@Tag(name = "药房工作台", description = "最小处方交接与发药结果查询，不返回完整病历")
public class PharmacyWorkbenchController {
  private final PharmacyWorkbenchService service;
  private final IdentityApi identity;

  public PharmacyWorkbenchController(PharmacyWorkbenchService service, IdentityApi identity) {
    this.service = service;
    this.identity = identity;
  }

  @GetMapping
  @Operation(
      summary = "待发或已发处方分页",
      description = "status 为 PENDING 或 DISPENSED；query 按宠物或客户姓名包含匹配；按开方时间及 ID 倒序。")
  public PageResponse<PrescriptionTaskResponse> list(
      @RequestParam(defaultValue = "PENDING") String status,
      @RequestParam(required = false) String query,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size) {
    return PageResponse.of(
        service.list(
            identity.currentUser(),
            status,
            query,
            PageRequest.of(
                Math.max(0, page),
                Math.clamp(size, 1, 100),
                Sort.by(Sort.Order.desc("issuedAt"), Sort.Order.desc("prescriptionId")))),
        v -> v);
  }

  @GetMapping("/{id}")
  @Operation(summary = "处方核对详情", description = "处方用法、患者联系摘要、当前有效过敏提示及可选发药结果；上游故障整体报错。")
  public PrescriptionReviewResponse review(@PathVariable UUID id) {
    return service.review(identity.currentUser(), id);
  }

  @GetMapping("/{id}/dispense")
  @Operation(summary = "只读核实发药结果", description = "不存在返回 404，不执行发药。网络结果未知时可先核实，再显式使用原幂等键重试。")
  public DispenseResponse result(@PathVariable UUID id) {
    return service.result(identity.currentUser(), id);
  }
}
