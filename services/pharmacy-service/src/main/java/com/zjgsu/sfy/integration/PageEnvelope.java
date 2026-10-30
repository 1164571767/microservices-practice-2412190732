package com.zjgsu.sfy.integration;

import java.util.List;
import org.springframework.data.domain.*;

public record PageEnvelope<T>(List<T> content, long totalElements) {
  public static <T> PageEnvelope<T> from(Page<T> page) {
    return new PageEnvelope<>(page.getContent(), page.getTotalElements());
  }

  public Page<T> page(Pageable pageable) {
    return new PageImpl<>(content, pageable, totalElements);
  }
}
