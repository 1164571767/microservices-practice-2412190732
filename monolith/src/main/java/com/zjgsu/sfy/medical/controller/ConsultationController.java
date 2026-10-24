package com.zjgsu.sfy.medical.controller;

import com.zjgsu.sfy.identity.IdentityApi;
import com.zjgsu.sfy.medical.repository.ConsultationRepository;
import com.zjgsu.sfy.medical.service.ConsultationService;
import com.zjgsu.sfy.medical.service.command.ConsultationDraft;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/medical/consultations/{appointmentId}")
@Tag(name = "诊疗工作台", description = "按预约保存草稿、原子提交病历和处方、完成诊疗及只读交接")
public class ConsultationController {
  private final ConsultationService service;
  private final IdentityApi identity;

  public ConsultationController(ConsultationService service, IdentityApi identity) {
    this.service = service;
    this.identity = identity;
  }

  public record SaveRequest(
      @NotNull @Min(0) Long version, @NotNull @Valid ConsultationDraft draft) {}

  public record VersionRequest(@NotNull @Min(0) Long version) {}

  public record CompleteRequest(@NotNull @Min(0) Long version, @NotNull Boolean noPrescription) {}

  @GetMapping
  @PreAuthorize("hasAnyRole('DOCTOR','STAFF','ADMIN')")
  @Operation(
      summary = "按预约恢复诊疗",
      description = "接诊医生可看本人草稿；临床员工可读正式病历与独立完成状态。未创建草稿时version=0，读取无写入副作用。")
  public ConsultationService.Detail get(@PathVariable UUID appointmentId) {
    return service.get(identity.currentUser(), appointmentId);
  }

  @PutMapping("/draft")
  @PreAuthorize("hasRole('DOCTOR')")
  @Operation(
      summary = "保存服务端草稿",
      description = "仅已叫号预约的接诊医生。允许不完整文本；version必填并校验，冲突409；正式提交或完成后不可覆盖。")
  public ConsultationService.Detail save(
      @PathVariable UUID appointmentId, @Valid @RequestBody SaveRequest request) {
    return service.saveDraft(
        identity.currentUser(), appointmentId, request.version(), request.draft());
  }

  @PostMapping("/submit")
  @PreAuthorize("hasRole('DOCTOR')")
  @Operation(
      summary = "提交正式病历和可选处方",
      description = "按已保存草稿及version，在同一事务创建病历、可选一张处方、交接索引及留痕。必须明确无需处方或开具处方。重复或旧版本409，无自动重放。")
  public ConsultationService.Detail submit(
      @PathVariable UUID appointmentId, @Valid @RequestBody VersionRequest request) {
    return service.submit(identity.currentUser(), appointmentId, request.version());
  }

  @PostMapping("/complete")
  @PreAuthorize("hasRole('DOCTOR')")
  @Operation(
      summary = "完成诊疗",
      description = "仅接诊医生；正式病历和处方决策均必需。noPrescription可用于明确历史病历无需处方。完成后只读；不推进收费或发药，不改变CALLED预约状态。")
  public ConsultationService.Detail complete(
      @PathVariable UUID appointmentId, @Valid @RequestBody CompleteRequest request) {
    return service.complete(
        identity.currentUser(), appointmentId, request.version(), request.noPrescription());
  }

  @GetMapping("/handoff")
  @PreAuthorize("hasAnyRole('DOCTOR','STAFF','ADMIN')")
  @Operation(
      summary = "核实收费与发药进度",
      description = "只读查询实际业务状态；非VOID有效账单含REFUNDED，发药凭证独立读取。依赖故障返回错误，不将未知显示为未办理。")
  public ConsultationService.Handoff handoff(@PathVariable UUID appointmentId) {
    return service.handoff(identity.currentUser(), appointmentId);
  }

  @GetMapping("/operations")
  @PreAuthorize("hasAnyRole('DOCTOR','STAFF','ADMIN')")
  @Operation(summary = "诊疗操作留痕", description = "每页20条，时间及ID倒序；仅动作和操作人ID，不包含草稿或诊断正文。")
  public List<ConsultationRepository.Operation> operations(
      @PathVariable UUID appointmentId, @RequestParam(defaultValue = "0") @Min(0) int page) {
    return service.operations(identity.currentUser(), appointmentId, page);
  }
}
