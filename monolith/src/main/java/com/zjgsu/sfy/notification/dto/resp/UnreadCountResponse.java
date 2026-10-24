package com.zjgsu.sfy.notification.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;

/** 未读通知数。 */
@Schema(description = "未读通知数")
public record UnreadCountResponse(@Schema(description = "当前未读通知条数", example = "3") long unread) {}
