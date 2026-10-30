package com.zjgsu.sfy.appointment.controller;

import com.zjgsu.sfy.appointment.common.AppointmentStatus;
import com.zjgsu.sfy.appointment.dto.resp.*;
import com.zjgsu.sfy.appointment.service.ReceptionQueryService;
import com.zjgsu.sfy.identity.IdentityApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/appointment")
@PreAuthorize("hasAnyRole('STAFF','DOCTOR','ADMIN')")
@Tag(name = "预约接待", description = "员工医生目录及按就诊日期检索预约，含必要联系资料")
public class ReceptionController {
  private final ReceptionQueryService queries;
  private final IdentityApi identity;

  public ReceptionController(ReceptionQueryService queries, IdentityApi identity) {
    this.queries = queries;
    this.identity = identity;
  }

  @GetMapping("/doctors")
  @Operation(summary = "可选医生目录", description = "仅启用且拥有 DOCTOR 角色的账号，仅返回 id 和 displayName。")
  public List<DoctorOptionResponse> doctors() {
    return queries.doctors(identity.currentUser());
  }

  @GetMapping("/appointments")
  @Operation(
      summary = "接待预约分页查询",
      description =
          "date 必填，按 Asia/Shanghai 的预约开始日期过滤；支持医生、状态和宠物筛选，按 startAt,id 稳定排序。含取消与历史记录，无诊疗内容。")
  public PageResponse<ReceptionAppointmentResponse> list(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      @RequestParam(required = false) UUID doctorId,
      @RequestParam(required = false) AppointmentStatus status,
      @RequestParam(required = false) UUID petId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return queries.list(identity.currentUser(), date, doctorId, status, petId, page, size);
  }
}
