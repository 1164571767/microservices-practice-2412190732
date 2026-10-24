package com.zjgsu.sfy.notification.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** 稳定的分页响应结构，不直接暴露 Spring Data 的 Page 序列化格式。 */
@Schema(description = "分页结果")
public record PageResponse<T>(
    @Schema(description = "当前页数据") List<T> items,
    @Schema(description = "当前页码，从 0 开始", example = "0") int page,
    @Schema(description = "每页条数", example = "20") int size,
    @Schema(description = "总记录数", example = "1") long totalElements,
    @Schema(description = "总页数", example = "1") int totalPages) {

  public static <S, T> PageResponse<T> of(Page<S> page, Function<S, T> mapper) {
    return new PageResponse<>(
        page.getContent().stream().map(mapper).toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }
}
