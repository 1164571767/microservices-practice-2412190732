package com.zjgsu.sfy.billing.controller;

import com.zjgsu.sfy.billing.common.BillStatus;
import com.zjgsu.sfy.billing.dto.req.BillReasonRequest;
import com.zjgsu.sfy.billing.dto.req.CreateBillRequest;
import com.zjgsu.sfy.billing.dto.resp.BillResponse;
import com.zjgsu.sfy.billing.dto.resp.BillSummaryResponse;
import com.zjgsu.sfy.billing.dto.resp.PageResponse;
import com.zjgsu.sfy.billing.service.BillService;
import com.zjgsu.sfy.identity.IdentityApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/billing/bills")
@Tag(name = "账单", description = "开单、查询、模拟支付、退款与作废；金额由服务端计算")
public class BillController {

  private final BillService billService;
  private final IdentityApi identityApi;

  public BillController(BillService billService, IdentityApi identityApi) {
    this.billService = billService;
    this.identityApi = identityApi;
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "宠物账单列表", description = "按宠物查账单，创建时间倒序，可按状态筛选。员工可查任意宠物，主人只能查自己宠物的账单。")
  public PageResponse<BillSummaryResponse> list(
      @Parameter(description = "宠物 ID", example = "3f1c2b4a-5d6e-4f70-8a91-b2c3d4e5f601")
          @RequestParam
          UUID petId,
      @Parameter(description = "账单状态筛选：UNPAID 待支付 / PAID 已支付 / REFUNDED 已退款 / VOID 已作废，可空")
          @RequestParam(required = false)
          BillStatus status,
      @Parameter(description = "页码，从 0 开始", example = "0") @RequestParam(defaultValue = "0")
          int page,
      @Parameter(description = "每页条数，1–100", example = "20") @RequestParam(defaultValue = "20")
          int size) {
    Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    return PageResponse.of(
        billService.list(identityApi.currentUser(), petId, status, pageable),
        BillSummaryResponse::from);
  }

  @GetMapping("/{billId}")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "账单详情", description = "返回账单明细、支付记录和操作流水。员工可查任意宠物，主人只能查自己宠物的账单。")
  public BillResponse get(
      @Parameter(description = "账单 ID", example = "9a1f2b3c-4d5e-4f60-8a7b-1c2d3e4f5a6b")
          @PathVariable
          UUID billId) {
    return BillResponse.from(billService.get(identityApi.currentUser(), billId));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "生成账单",
      description = "前台或管理员在收费台现场开单：按病历（可选处方）和收费项目生成账单，金额由服务端按价目表计算。带上 Idempotency-Key 请求头可以安全重试。")
  public BillResponse create(
      @Valid @RequestBody CreateBillRequest request,
      @Parameter(description = "幂等键，可选。同一个键重复提交同一请求会返回第一次生成的账单", example = "bill-2026-0001")
          @RequestHeader(value = "Idempotency-Key", required = false)
          String idempotencyKey) {
    return BillResponse.from(
        billService.create(identityApi.currentUser(), request.toCommand(), idempotencyKey));
  }

  @PostMapping("/{billId}/pay")
  @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "模拟支付",
      description = "把待支付账单标记为已支付（本期为模拟支付，没有真实扣款）。带上 Idempotency-Key 重复提交会回放同一笔支付；不带则重复支付返回已支付错误。")
  public BillResponse pay(
      @Parameter(description = "账单 ID", example = "9a1f2b3c-4d5e-4f60-8a7b-1c2d3e4f5a6b")
          @PathVariable
          UUID billId,
      @Parameter(description = "幂等键，可选。同一个键重复提交同一请求会回放同一笔支付", example = "pay-2026-0001")
          @RequestHeader(value = "Idempotency-Key", required = false)
          String idempotencyKey) {
    return BillResponse.from(billService.pay(identityApi.currentUser(), billId, idempotencyKey));
  }

  @PostMapping("/{billId}/refund")
  @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
  @Operation(summary = "账单退款", description = "前台或管理员对已支付账单退款，支付记录同时转为已退款。只有「已支付」状态可以退款，原因会写入操作流水。")
  public BillResponse refund(
      @Parameter(description = "账单 ID", example = "9a1f2b3c-4d5e-4f60-8a7b-1c2d3e4f5a6b")
          @PathVariable
          UUID billId,
      @Valid @RequestBody BillReasonRequest request) {
    return BillResponse.from(
        billService.refund(identityApi.currentUser(), billId, request.reason()));
  }

  @PostMapping("/{billId}/void")
  @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
  @Operation(summary = "作废账单", description = "前台或管理员作废一张还没支付的账单，作废后不能再支付。只有「待支付」状态可以作废，原因会写入操作流水。")
  public BillResponse voidBill(
      @Parameter(description = "账单 ID", example = "9a1f2b3c-4d5e-4f60-8a7b-1c2d3e4f5a6b")
          @PathVariable
          UUID billId,
      @Valid @RequestBody BillReasonRequest request) {
    return BillResponse.from(
        billService.voidBill(identityApi.currentUser(), billId, request.reason()));
  }
}
