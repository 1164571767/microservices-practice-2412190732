package com.zjgsu.sfy.common.api;

import com.zjgsu.sfy.common.error.BusinessException;
import com.zjgsu.sfy.common.error.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test/errors")
public class ErrorProbeController {

  @GetMapping("/missing")
  void missing() {
    throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
  }

  @GetMapping("/boom")
  void boom() {
    throw new IllegalStateException("boom");
  }

  @PostMapping("/validate")
  void validate(@Valid @RequestBody Payload payload) {}

  record Payload(@NotNull String name) {}
}
