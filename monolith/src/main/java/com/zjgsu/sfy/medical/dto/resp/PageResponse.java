package com.zjgsu.sfy.medical.dto.resp;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** 稳定的分页响应结构，不直接暴露 Spring Data 的 Page 序列化格式。 */
public record PageResponse<T>(
    List<T> items, int page, int size, long totalElements, int totalPages) {

  public static <S, T> PageResponse<T> of(Page<S> page, Function<S, T> mapper) {
    return new PageResponse<>(
        page.getContent().stream().map(mapper).toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }
}
