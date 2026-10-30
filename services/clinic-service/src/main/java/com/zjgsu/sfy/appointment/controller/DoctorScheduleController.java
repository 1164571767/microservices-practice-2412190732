package com.zjgsu.sfy.appointment.controller;

import com.zjgsu.sfy.appointment.common.ScheduleState;
import com.zjgsu.sfy.appointment.dto.req.CreateScheduleRequest;
import com.zjgsu.sfy.appointment.dto.req.ScheduleChangeRequest;
import com.zjgsu.sfy.appointment.dto.resp.ScheduleResponse;
import com.zjgsu.sfy.appointment.dto.resp.TimeSlotResponse;
import com.zjgsu.sfy.appointment.service.DoctorScheduleService;
import com.zjgsu.sfy.appointment.service.result.ScheduleOperation;
import com.zjgsu.sfy.identity.IdentityApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/appointment")
@Tag(name = "医生排班", description = "医生可接诊时段与号源；排班增删仅管理员，查询所有登录用户可用")
public class DoctorScheduleController {

  private final DoctorScheduleService scheduleService;
  private final IdentityApi identityApi;

  public DoctorScheduleController(DoctorScheduleService scheduleService, IdentityApi identityApi) {
    this.scheduleService = scheduleService;
    this.identityApi = identityApi;
  }

  @PostMapping("/schedules")
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "新建排班",
      description = "仅管理员可用。给某位医生设定一段可接诊时间，按 slotMinutes 切成多个号源；同一医生的时段不能重叠。")
  public ScheduleResponse createSchedule(@Valid @RequestBody CreateScheduleRequest request) {
    return ScheduleResponse.from(
        scheduleService.createSchedule(identityApi.currentUser(), request.toCommand()));
  }

  @GetMapping("/schedules")
  @Operation(summary = "查看医生排班", description = "返回指定医生的全部排班时段，所有登录用户都可以查。")
  public List<ScheduleResponse> listSchedules(
      @Parameter(description = "医生（账号）ID", example = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d")
          @RequestParam
          UUID doctorId) {
    return scheduleService.listSchedules(doctorId).stream().map(ScheduleResponse::from).toList();
  }

  @DeleteMapping("/schedules/{scheduleId}")
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "受保护删除排班", description = "仅管理员；仅未开始且无任何预约历史的排班允许删除，必须提供版本与原因。审计保留。")
  public void deleteSchedule(
      @Parameter(description = "排班 ID", example = "7e8f9a0b-1c2d-4e3f-8a5b-6c7d8e9f0a1b")
          @PathVariable
          UUID scheduleId,
      @RequestParam @Min(0) long version,
      @RequestParam @NotBlank @Size(max = 500) String reason) {
    scheduleService.deleteSchedule(identityApi.currentUser(), scheduleId, version, reason.trim());
  }

  @PostMapping("/schedules/{id}/stop-booking")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "停止放号", description = "禁止新增和改入；既有预约继续履约。")
  public ScheduleResponse stopBooking(
      @PathVariable UUID id, @Valid @RequestBody ScheduleChangeRequest request) {
    return ScheduleResponse.from(
        scheduleService.changeState(
            identityApi.currentUser(),
            id,
            request.version(),
            request.reason().trim(),
            ScheduleState.BOOKING_CLOSED));
  }

  @PostMapping("/schedules/{id}/reopen")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "恢复放号", description = "仅停止放号且尚未结束的排班；重新核验启用医生与冲突。")
  public ScheduleResponse reopen(
      @PathVariable UUID id, @Valid @RequestBody ScheduleChangeRequest request) {
    return ScheduleResponse.from(
        scheduleService.changeState(
            identityApi.currentUser(),
            id,
            request.version(),
            request.reason().trim(),
            ScheduleState.OPEN));
  }

  @PostMapping("/schedules/{id}/cancel")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "停诊", description = "须先人工处理所有未取消预约；不自动取消、转诊或通知；停诊不可恢复。")
  public ScheduleResponse cancel(
      @PathVariable UUID id, @Valid @RequestBody ScheduleChangeRequest request) {
    return ScheduleResponse.from(
        scheduleService.changeState(
            identityApi.currentUser(),
            id,
            request.version(),
            request.reason().trim(),
            ScheduleState.CANCELLED));
  }

  @GetMapping("/schedules/{id}/operations")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "排班操作留痕", description = "每页20条，时间和ID倒序；删除排班后仍可读取。")
  public List<ScheduleOperation> operations(
      @PathVariable UUID id, @RequestParam(defaultValue = "0") @Min(0) int page) {
    return scheduleService.operations(identityApi.currentUser(), id, page);
  }

  @GetMapping("/availability")
  @Operation(
      summary = "查询可预约时段",
      description = "按医生和日期查当天还能约的时段，已约满的不会返回。date 按 Asia/Shanghai 时区解释，时间返回 UTC。")
  public List<TimeSlotResponse> availability(
      @Parameter(description = "医生（账号）ID", example = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d")
          @RequestParam
          UUID doctorId,
      @Parameter(description = "就诊日期，格式 yyyy-MM-dd", example = "2026-11-10")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date) {
    return scheduleService.availability(doctorId, date).stream()
        .map(TimeSlotResponse::from)
        .toList();
  }
}
