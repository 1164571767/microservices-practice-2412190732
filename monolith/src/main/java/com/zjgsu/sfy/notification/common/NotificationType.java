package com.zjgsu.sfy.notification.common;

import io.swagger.v3.oas.annotations.media.Schema;

/** 站内通知类型；与通知来源事件一一对应，作为稳定对外标识。 */
@Schema(
    description =
        "通知类型：APPOINTMENT_BOOKED 预约成功 / QUEUE_CALLED 候诊叫号 / BILL_PAID 收费成功 / REVISIT_REMINDER 复诊提醒 / LOW_STOCK 低库存预警")
public enum NotificationType {
  /** 预约成功（通知主人）。 */
  APPOINTMENT_BOOKED,
  /** 候诊叫号（通知主人）。 */
  QUEUE_CALLED,
  /** 收费成功（通知主人）。 */
  BILL_PAID,
  /** 复诊提醒（定时任务生成，通知主人）。 */
  REVISIT_REMINDER,
  /** 低库存预警（通知药房 / 管理员）。 */
  LOW_STOCK
}
