package com.zjgsu.sfy.pharmacy.controller;

import com.zjgsu.sfy.identity.IdentityApi;
import com.zjgsu.sfy.pharmacy.dto.req.AdjustInventoryRequest;
import com.zjgsu.sfy.pharmacy.dto.resp.InventoryAdjustmentResponse;
import com.zjgsu.sfy.pharmacy.dto.resp.InventoryResponse;
import com.zjgsu.sfy.pharmacy.dto.resp.PageResponse;
import com.zjgsu.sfy.pharmacy.service.InventoryService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pharmacy/inventory")
@Tag(name = "药品库存", description = "库存查询、低库存预警与人工调整流水；调整仅药房和管理员可做")
public class InventoryController {

  private static final String READ_ROLES = "hasAnyRole('PHARMACY','ADMIN','STAFF','DOCTOR')";

  private final InventoryService inventoryService;
  private final IdentityApi identityApi;

  public InventoryController(InventoryService inventoryService, IdentityApi identityApi) {
    this.inventoryService = inventoryService;
    this.identityApi = identityApi;
  }

  @GetMapping
  @PreAuthorize(READ_ROLES)
  @Operation(summary = "库存列表", description = "药房、管理员、前台、医生可查。分页返回各药品当前库存，可按名称或规格模糊搜索。")
  public PageResponse<InventoryResponse> list(
      @Parameter(description = "页码，从 0 开始", example = "0") @RequestParam(defaultValue = "0")
          int page,
      @Parameter(description = "每页条数，1–100", example = "20") @RequestParam(defaultValue = "20")
          int size,
      @Parameter(description = "搜索关键字，匹配药品名称或规格，可空", example = "阿莫西林")
          @RequestParam(required = false)
          String query) {
    return PageResponse.of(
        inventoryService.list(query, false, pageable(page, size)), InventoryResponse::from);
  }

  @GetMapping("/low-stock")
  @PreAuthorize(READ_ROLES)
  @Operation(summary = "低库存预警列表", description = "只返回库存低于各自阈值的药品，用于补货提醒；库存刚好等于阈值不算低库存。")
  public PageResponse<InventoryResponse> lowStock(
      @Parameter(description = "页码，从 0 开始", example = "0") @RequestParam(defaultValue = "0")
          int page,
      @Parameter(description = "每页条数，1–100", example = "20") @RequestParam(defaultValue = "20")
          int size,
      @Parameter(description = "搜索关键字，匹配药品名称或规格，可空", example = "阿莫西林")
          @RequestParam(required = false)
          String query) {
    return PageResponse.of(
        inventoryService.list(query, true, pageable(page, size)), InventoryResponse::from);
  }

  @GetMapping("/{drugId}")
  @PreAuthorize(READ_ROLES)
  @Operation(summary = "单药库存", description = "按药品 ID 查看当前库存数量与是否低于阈值。")
  public InventoryResponse get(
      @Parameter(description = "药品 ID", example = "6a7b8c9d-0e1f-4a2b-9c3d-4e5f6a7b8c9d")
          @PathVariable
          UUID drugId) {
    return InventoryResponse.from(inventoryService.get(drugId));
  }

  @PostMapping("/{drugId}/adjustments")
  @PreAuthorize("hasAnyRole('PHARMACY','ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "调整库存",
      description = "仅药房或管理员。正数表示入库、负数表示出库，调整数量不能为 0，扣减后不能为负；每笔调整都会留下流水，记录原因和操作人。")
  public InventoryAdjustmentResponse adjust(
      @Parameter(description = "药品 ID", example = "6a7b8c9d-0e1f-4a2b-9c3d-4e5f6a7b8c9d")
          @PathVariable
          UUID drugId,
      @Valid @RequestBody AdjustInventoryRequest request) {
    return InventoryAdjustmentResponse.from(
        inventoryService
            .adjust(identityApi.currentUser(), drugId, request.toCommand())
            .adjustment());
  }

  @GetMapping("/{drugId}/adjustments")
  @PreAuthorize(READ_ROLES)
  @Operation(summary = "库存调整流水", description = "按时间倒序分页查看某药品的历史库存变动，含调整前后数量、原因和操作人。")
  public PageResponse<InventoryAdjustmentResponse> adjustments(
      @Parameter(description = "药品 ID", example = "6a7b8c9d-0e1f-4a2b-9c3d-4e5f6a7b8c9d")
          @PathVariable
          UUID drugId,
      @Parameter(description = "页码，从 0 开始", example = "0") @RequestParam(defaultValue = "0")
          int page,
      @Parameter(description = "每页条数，1–100", example = "20") @RequestParam(defaultValue = "20")
          int size) {
    Pageable pageable =
        PageRequest.of(
            Math.max(page, 0),
            Math.min(Math.max(size, 1), 100),
            Sort.by(Sort.Direction.DESC, "createdAt"));
    return PageResponse.of(
        inventoryService.adjustments(drugId, pageable), InventoryAdjustmentResponse::from);
  }

  private static Pageable pageable(int page, int size) {
    return PageRequest.of(
        Math.max(page, 0),
        Math.min(Math.max(size, 1), 100),
        Sort.by(Sort.Direction.ASC, "drug.name").and(Sort.by(Sort.Direction.ASC, "drugId")));
  }
}
