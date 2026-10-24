package com.zjgsu.sfy.appointment.dto.resp;

import com.zjgsu.sfy.owner.PatientContactSummary;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "接待预约与必要联系资料；历史档案缺失时 patient 为空")
public record ReceptionAppointmentResponse(
    AppointmentResponse appointment,
    PatientContactSummary patient,
    String doctorName,
    com.zjgsu.sfy.medical.ConsultationSummary consultation) {}
