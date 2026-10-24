package com.zjgsu.sfy.notification.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;

/** 一键已读结果：本次实际由未读转为已读的条数（重复执行时为 0）。 */
@Schema(description = "一键已读的结果")
public record MarkAllReadResponse(
    @Schema(description = "本次实际标记为已读的条数，重复调用为 0", example = "2") int updated) {}
