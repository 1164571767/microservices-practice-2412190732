package com.zjgsu.sfy.notification.dto.resp;

import com.zjgsu.sfy.notification.entity.Notification;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** 站内通知响应；不直接暴露实体。 */
@Schema(description = "站内通知")
public record NotificationResponse(
    @Schema(description = "通知 ID", example = "4f5a6b7c-8d9e-4f0a-9b1c-2d3e4f5a6b7c") UUID id,
    @Schema(
            description =
                "通知类型：APPOINTMENT_BOOKED 预约成功 / QUEUE_CALLED 叫号 / BILL_PAID 收费成功 / REVISIT_REMINDER 复诊提醒 / LOW_STOCK 低库存预警",
            example = "APPOINTMENT_BOOKED")
        String type,
    @Schema(description = "标题，最多 120 字", example = "预约成功") String title,
    @Schema(description = "正文，最多 1000 字", example = "宠物「旺财」已预约李医生，时间 2026-11-10 09:00 至 09:30。")
        String body,
    @Schema(description = "阅读状态：UNREAD 未读 / READ 已读", example = "UNREAD") String status,
    @Schema(
            description = "来源业务 ID，如预约 ID、账单 ID、药品 ID",
            example = "4a5b6c7d-8e9f-4a0b-9c1d-2e3f4a5b6c7d")
        UUID sourceId,
    @Schema(description = "创建时间（UTC）", example = "2026-10-20T01:05:00Z") Instant createdAt,
    @Schema(description = "已读时间（UTC），未读为空", example = "2026-10-20T01:06:00Z") Instant readAt) {

  public static NotificationResponse from(Notification notification) {
    return new NotificationResponse(
        notification.getId(),
        notification.getType().name(),
        notification.getTitle(),
        notification.getBody(),
        notification.getStatus().name(),
        notification.getSourceId(),
        notification.getCreatedAt(),
        notification.getReadAt());
  }
}
