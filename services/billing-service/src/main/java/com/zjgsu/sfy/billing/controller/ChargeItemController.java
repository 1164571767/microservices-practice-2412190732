package com.zjgsu.sfy.billing.controller;

import com.zjgsu.sfy.billing.dto.req.CreateChargeItemRequest;
import com.zjgsu.sfy.billing.dto.req.UpdateChargeItemRequest;
import com.zjgsu.sfy.billing.dto.resp.ChargeItemResponse;
import com.zjgsu.sfy.billing.dto.resp.PageResponse;
import com.zjgsu.sfy.billing.service.ChargeItemService;
import com.zjgsu.sfy.identity.IdentityApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/billing/charge-items")
@Tag(name = "收费项目", description = "收费价目表；前台、医生、管理员可查，仅管理员可维护")
public class ChargeItemController {

  private static final String READ_ROLES = "hasAnyRole('STAFF','DOCTOR','ADMIN')";

  private final ChargeItemService chargeItemService;
  private final IdentityApi identityApi;

  public ChargeItemController(ChargeItemService chargeItemService, IdentityApi identityApi) {
    this.chargeItemService = chargeItemService;
    this.identityApi = identityApi;
  }

  @GetMapping
  @PreAuthorize(READ_ROLES)
  @Operation(summary = "收费项目列表", description = "前台、医生、管理员可查。分页返回价目表，可按名称模糊搜索，按名称升序排列。")
  public PageResponse<ChargeItemResponse> list(
      @Parameter(description = "页码，从 0 开始", example = "0") @RequestParam(defaultValue = "0")
          int page,
      @Parameter(description = "每页条数，1–100", example = "20") @RequestParam(defaultValue = "20")
          int size,
      @Parameter(description = "搜索关键字，匹配收费项目名称，可空", example = "诊查费") @RequestParam(required = false)
          String query) {
    Pageable pageable =
        PageRequest.of(
            Math.max(page, 0),
            Math.min(Math.max(size, 1), 100),
            Sort.by(Sort.Direction.ASC, "name").and(Sort.by(Sort.Direction.ASC, "id")));
    return PageResponse.of(chargeItemService.list(query, pageable), ChargeItemResponse::from);
  }

  @GetMapping("/{chargeItemId}")
  @PreAuthorize(READ_ROLES)
  @Operation(summary = "收费项目详情", description = "按 ID 查看单个收费项目的名称、金额和启停状态。")
  public ChargeItemResponse get(
      @Parameter(description = "收费项目 ID", example = "0c1d2e3f-4a5b-4c6d-8e7f-9a0b1c2d3e4f")
          @PathVariable
          UUID chargeItemId) {
    return ChargeItemResponse.from(chargeItemService.get(chargeItemId));
  }

  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "新增收费项目", description = "仅管理员。新增一个收费项目（如诊查费、药费）；名称不能重复，金额由管理员定价。")
  public ChargeItemResponse create(@Valid @RequestBody CreateChargeItemRequest request) {
    return ChargeItemResponse.from(
        chargeItemService.create(identityApi.currentUser(), request.toCommand()));
  }

  @PutMapping("/{chargeItemId}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "修改收费项目", description = "仅管理员。整体更新名称、金额和启停状态；停用后不能再用于生成新账单，已生成的账单保留原价。")
  public ChargeItemResponse update(
      @Parameter(description = "收费项目 ID", example = "0c1d2e3f-4a5b-4c6d-8e7f-9a0b1c2d3e4f")
          @PathVariable
          UUID chargeItemId,
      @Valid @RequestBody UpdateChargeItemRequest request) {
    return ChargeItemResponse.from(
        chargeItemService.update(identityApi.currentUser(), chargeItemId, request.toCommand()));
  }
}
