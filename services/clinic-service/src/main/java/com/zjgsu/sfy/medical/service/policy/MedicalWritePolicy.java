package com.zjgsu.sfy.medical.service.policy;

import com.zjgsu.sfy.common.error.BusinessException;
import com.zjgsu.sfy.common.error.ErrorCode;
import com.zjgsu.sfy.identity.CurrentUser;
import com.zjgsu.sfy.medical.AppointmentLookup.AppointmentInfo;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 写病历 / 开处方的前置校验入口。
 *
 * <p>基线校验「仅接诊医生可写」**不可移除**（写死在此，无法通过移除某个 bean 绕过）； {@link MedicalWriteGuard}
 * 作为可插拔的**追加**校验在基线之后执行，未来扩展（如要求预约已到院叫号）无需改动用例。
 */
@Component
public class MedicalWritePolicy {

  private final List<MedicalWriteGuard> additionalGuards;

  public MedicalWritePolicy(List<MedicalWriteGuard> additionalGuards) {
    this.additionalGuards = List.copyOf(additionalGuards);
  }

  public void enforce(CurrentUser requester, AppointmentInfo appointment) {
    if (!requester.userId().equals(appointment.doctorId())) {
      throw new BusinessException(ErrorCode.AUTH_FORBIDDEN, "仅接诊医生可写");
    }
    for (MedicalWriteGuard guard : additionalGuards) {
      guard.check(requester, appointment);
    }
  }
}
