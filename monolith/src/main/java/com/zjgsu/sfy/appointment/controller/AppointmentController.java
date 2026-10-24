package com.zjgsu.sfy.appointment.controller;

import com.zjgsu.sfy.appointment.dto.req.BookAppointmentRequest;
import com.zjgsu.sfy.appointment.dto.req.RescheduleRequest;
import com.zjgsu.sfy.appointment.dto.resp.AppointmentResponse;
import com.zjgsu.sfy.appointment.service.AppointmentService;
import com.zjgsu.sfy.identity.IdentityApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/appointment")
@Tag(name = "预约", description = "宠物挂号预约：创建、查询、取消、改期与到院签到")
public class AppointmentController {

  private final AppointmentService appointmentService;
  private final IdentityApi identityApi;

  public AppointmentController(AppointmentService appointmentService, IdentityApi identityApi) {
    this.appointmentService = appointmentService;
    this.identityApi = identityApi;
  }

  @PostMapping("/appointments")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "创建预约",
      description =
          "宠物主人给自己的宠物约号，前台也可以代客预约。时段必须在医生排班内且没被占用；带上 Idempotency-Key 请求头可以安全重试，重复提交不会多出一单。")
  public AppointmentResponse book(
      @Parameter(description = "幂等键，可选。同一个键重复提交同一请求会返回第一次创建的预约", example = "booking-2026-0001")
          @RequestHeader(value = "Idempotency-Key", required = false)
          String idempotencyKey,
      @Valid @RequestBody BookAppointmentRequest request) {
    return AppointmentResponse.from(
        appointmentService.book(identityApi.currentUser(), request.toCommand(), idempotencyKey));
  }

  @GetMapping("/appointments/{appointmentId}")
  @Operation(summary = "预约详情", description = "宠物主人只能看自己宠物的预约，前台、医生、管理员可以看任意预约。")
  public AppointmentResponse getAppointment(
      @Parameter(description = "预约 ID", example = "4a5b6c7d-8e9f-4a0b-9c1d-2e3f4a5b6c7d")
          @PathVariable
          UUID appointmentId) {
    return AppointmentResponse.from(
        appointmentService.getAppointment(identityApi.currentUser(), appointmentId));
  }

  @GetMapping("/pets/{petId}/appointments")
  @Operation(summary = "某宠物的预约列表", description = "返回该宠物的全部预约（含历史）。宠物主人只能查自己宠物，员工可查任意宠物。")
  public List<AppointmentResponse> listByPet(
      @Parameter(description = "宠物 ID", example = "3f1c2b4a-5d6e-4f70-8a91-b2c3d4e5f601")
          @PathVariable
          UUID petId) {
    return appointmentService.listByPet(identityApi.currentUser(), petId).stream()
        .map(AppointmentResponse::from)
        .toList();
  }

  @PostMapping("/appointments/{appointmentId}/cancel")
  @Operation(summary = "取消预约", description = "「已预约」或「已签到」状态可以取消，已叫号或已取消的不能取消。主人只能取消自己宠物的预约。")
  public AppointmentResponse cancel(
      @Parameter(description = "预约 ID", example = "4a5b6c7d-8e9f-4a0b-9c1d-2e3f4a5b6c7d")
          @PathVariable
          UUID appointmentId) {
    return AppointmentResponse.from(
        appointmentService.cancel(identityApi.currentUser(), appointmentId));
  }

  @PostMapping("/appointments/{appointmentId}/reschedule")
  @Operation(summary = "预约改期", description = "只在「已预约」状态可以改期。新时段必须可约，改期会复用原预约，不会新建一条记录。")
  public AppointmentResponse reschedule(
      @Parameter(description = "预约 ID", example = "4a5b6c7d-8e9f-4a0b-9c1d-2e3f4a5b6c7d")
          @PathVariable
          UUID appointmentId,
      @Valid @RequestBody RescheduleRequest request) {
    return AppointmentResponse.from(
        appointmentService.reschedule(
            identityApi.currentUser(), appointmentId, request.toCommand()));
  }

  @PostMapping("/appointments/{appointmentId}/check-in")
  @PreAuthorize("hasAnyRole('STAFF','DOCTOR','ADMIN')")
  @Operation(summary = "到院签到", description = "前台、医生或管理员在宠物到院时操作。只有「已预约」状态能签到，签到后进入该医生的候诊队列。")
  public AppointmentResponse checkIn(
      @Parameter(description = "预约 ID", example = "4a5b6c7d-8e9f-4a0b-9c1d-2e3f4a5b6c7d")
          @PathVariable
          UUID appointmentId) {
    return AppointmentResponse.from(
        appointmentService.checkIn(identityApi.currentUser(), appointmentId));
  }
}
