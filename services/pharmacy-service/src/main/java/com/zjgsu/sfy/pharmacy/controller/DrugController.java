package com.zjgsu.sfy.pharmacy.controller;

import com.zjgsu.sfy.identity.IdentityApi;
import com.zjgsu.sfy.pharmacy.dto.req.CreateDrugRequest;
import com.zjgsu.sfy.pharmacy.dto.req.UpdateDrugRequest;
import com.zjgsu.sfy.pharmacy.dto.resp.DrugResponse;
import com.zjgsu.sfy.pharmacy.dto.resp.PageResponse;
import com.zjgsu.sfy.pharmacy.service.DrugService;
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
@RequestMapping("/api/pharmacy/drugs")
@Tag(name = "药品目录", description = "药品档案维护；药房和管理员可写，其他临床角色只读")
public class DrugController {

  private static final String READ_ROLES = "hasAnyRole('PHARMACY','ADMIN','STAFF','DOCTOR')";

  private final DrugService drugService;
  private final IdentityApi identityApi;

  public DrugController(DrugService drugService, IdentityApi identityApi) {
    this.drugService = drugService;
    this.identityApi = identityApi;
  }

  @GetMapping
  @PreAuthorize(READ_ROLES)
  @Operation(summary = "药品目录列表", description = "药房、管理员、前台、医生可查。可按名称或规格模糊搜索，按名称升序分页。")
  public PageResponse<DrugResponse> list(
      @Parameter(description = "页码，从 0 开始", example = "0") @RequestParam(defaultValue = "0")
          int page,
      @Parameter(description = "每页条数，1–100", example = "20") @RequestParam(defaultValue = "20")
          int size,
      @Parameter(description = "搜索关键字，匹配药品名称或规格，可空", example = "阿莫西林")
          @RequestParam(required = false)
          String query) {
    Pageable pageable =
        PageRequest.of(
            Math.max(page, 0),
            Math.min(Math.max(size, 1), 100),
            Sort.by(Sort.Direction.ASC, "name").and(Sort.by(Sort.Direction.ASC, "id")));
    return PageResponse.of(drugService.list(query, pageable), DrugResponse::from);
  }

  @GetMapping("/{drugId}")
  @PreAuthorize(READ_ROLES)
  @Operation(summary = "药品详情", description = "按药品 ID 查档案，临床角色都可读。")
  public DrugResponse get(
      @Parameter(description = "药品 ID", example = "6a7b8c9d-0e1f-4a2b-9c3d-4e5f6a7b8c9d")
          @PathVariable
          UUID drugId) {
    return DrugResponse.from(drugService.get(drugId));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('PHARMACY','ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "新增药品", description = "仅药房或管理员。新建药品档案时可以顺手初始化库存；同名同规格的药品不能重复创建。")
  public DrugResponse create(@Valid @RequestBody CreateDrugRequest request) {
    return DrugResponse.from(drugService.create(identityApi.currentUser(), request.toCommand()));
  }

  @PutMapping("/{drugId}")
  @PreAuthorize("hasAnyRole('PHARMACY','ADMIN')")
  @Operation(summary = "修改药品", description = "仅药房或管理员。整体更新名称、规格、单位、低库存阈值和启停状态；停用后不能再发药，历史库存和流水仍保留。")
  public DrugResponse update(
      @Parameter(description = "药品 ID", example = "6a7b8c9d-0e1f-4a2b-9c3d-4e5f6a7b8c9d")
          @PathVariable
          UUID drugId,
      @Valid @RequestBody UpdateDrugRequest request) {
    return DrugResponse.from(
        drugService.update(identityApi.currentUser(), drugId, request.toCommand()));
  }
}
