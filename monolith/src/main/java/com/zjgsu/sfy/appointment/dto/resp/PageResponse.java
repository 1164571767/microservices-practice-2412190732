package com.zjgsu.sfy.appointment.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "预约分页结果，页码从 0 开始")
public record PageResponse<T>(
    List<T> items, int page, int size, long totalElements, int totalPages) {}
