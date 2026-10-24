package com.zjgsu.sfy.medical;

import java.util.Optional;
import java.util.UUID;

/**
 * medical 模块为「接诊医生校验」所需的最小预约只读端口。
 *
 * <p>由 {@code appointment} 模块实现（依赖方向 appointment → medical），从而 medical 无需依赖 appointment
 * 即可取得接诊医生与宠物归属，避免模块循环依赖（见 ADR-0006）。
 */
public interface AppointmentLookup {

  /**
   * @return 该预约的只读视图；不存在返回空。
   */
  Optional<AppointmentInfo> findById(UUID appointmentId);

  /** Stable lock shared by all medical writes and appointment lifecycle operations. */
  Optional<AppointmentInfo> lockById(UUID appointmentId);

  /** 接诊所需的最小字段子集。 */
  record AppointmentInfo(UUID id, UUID petId, UUID doctorId, String status) {}
}
