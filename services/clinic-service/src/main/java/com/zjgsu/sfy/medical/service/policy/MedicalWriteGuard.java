package com.zjgsu.sfy.medical.service.policy;

import com.zjgsu.sfy.identity.CurrentUser;
import com.zjgsu.sfy.medical.AppointmentLookup.AppointmentInfo;

/**
 * 写病历 / 开处方的**可插拔**追加校验，在基线「仅接诊医生」之后执行。
 *
 * <p>实现类注册为 Spring bean 即生效，用于追加业务前置条件（如要求预约已到院叫号）而无需改动用例。 基线接诊医生校验由 {@link MedicalWritePolicy}
 * 保证，无法通过移除本接口的实现绕过。
 */
public interface MedicalWriteGuard {

  void check(CurrentUser requester, AppointmentInfo appointment);
}
