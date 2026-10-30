package com.zjgsu.sfy.medical.service.policy;

import com.zjgsu.sfy.common.error.*;
import com.zjgsu.sfy.identity.CurrentUser;
import com.zjgsu.sfy.medical.AppointmentLookup.AppointmentInfo;
import com.zjgsu.sfy.medical.repository.ConsultationRepository;
import org.springframework.stereotype.Component;

@Component
public class ConsultationWriteGuard implements MedicalWriteGuard {
  private final ConsultationRepository consultations;

  public ConsultationWriteGuard(ConsultationRepository consultations) {
    this.consultations = consultations;
  }

  @Override
  public void check(CurrentUser user, AppointmentInfo appointment) {
    if (!user.isDoctor()) throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
    if (!"CALLED".equals(appointment.status()))
      throw new BusinessException(ErrorCode.APPOINTMENT_INVALID_STATE, "仅已叫号预约可提交诊疗");
    if (consultations.get(appointment.id()).completedAt() != null)
      throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "诊疗已完成，正式记录只读");
    if ("NONE".equals(consultations.get(appointment.id()).prescriptionChoice()))
      throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "已正式确认无需处方，记录只读");
  }
}
