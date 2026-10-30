package com.zjgsu.sfy.appointment.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "可接诊的启用医生，仅提供选择所需资料")
public record DoctorOptionResponse(UUID id, String displayName) {}
