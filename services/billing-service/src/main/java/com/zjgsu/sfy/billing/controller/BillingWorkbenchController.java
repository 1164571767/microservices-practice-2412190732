package com.zjgsu.sfy.billing.controller;

import com.zjgsu.sfy.billing.common.BillStatus;
import com.zjgsu.sfy.billing.dto.resp.*;
import com.zjgsu.sfy.billing.service.BillingWorkbenchService;
import com.zjgsu.sfy.identity.IdentityApi;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/billing/workbench")
@PreAuthorize("hasAnyRole('STAFF','ADMIN')")
public class BillingWorkbenchController {
  private final BillingWorkbenchService service;
  private final IdentityApi identity;

  public BillingWorkbenchController(BillingWorkbenchService service, IdentityApi identity) {
    this.service = service;
    this.identity = identity;
  }

  @GetMapping("/bills")
  @Operation(summary = "收费工作台账单分页", description = "前台或管理员按宠物和状态筛选；不传宠物查询全部。联系资料按页批量装配，保留归档历史。")
  public PageResponse<WorkbenchBillResponse> bills(
      @RequestParam(required = false) UUID petId,
      @RequestParam(required = false) BillStatus status,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return PageResponse.of(
        service.list(
            identity.currentUser(),
            petId,
            status,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"))),
        row -> row);
  }

  @GetMapping("/records")
  @Operation(summary = "开单病历最小索引", description = "仅返回病历标识、就诊时间、医生、可选处方标识及有效账单占用情况，不返回诊疗正文。")
  public PageResponse<BillingRecordResponse> records(
      @RequestParam UUID petId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
    return PageResponse.of(
        service.records(
            identity.currentUser(),
            petId,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "visitedAt", "id"))),
        row -> row);
  }
}
