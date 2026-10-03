package com.zjgsu.sfy.system.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemStatusController {

  private final String application;
  private final String version;

  public SystemStatusController(
      @Value("${spring.application.name}") String application,
      @Value("${info.app.version:unknown}") String version) {
    this.application = application;
    this.version = version;
  }

  @GetMapping("/status")
  public StatusResponse status() {
    return new StatusResponse(application, "UP", version);
  }

  public record StatusResponse(String application, String status, String version) {}
}
