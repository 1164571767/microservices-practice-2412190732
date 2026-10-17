package com.zjgsu.sfy.system.controller;

import com.zjgsu.sfy.system.dto.resp.StatusResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemStatusController {

  private static final Logger log = LoggerFactory.getLogger(SystemStatusController.class);

  private final String application;
  private final String version;
  private final Counter statusRequests;

  public SystemStatusController(
      @Value("${spring.application.name}") String application,
      @Value("${info.app.version:unknown}") String version,
      MeterRegistry meterRegistry) {
    this.application = application;
    this.version = version;
    this.statusRequests = Counter.builder("petcare.system.status.requests").register(meterRegistry);
  }

  @GetMapping("/status")
  public StatusResponse status() {
    statusRequests.increment();
    log.atInfo()
        .addKeyValue("application", application)
        .addKeyValue("status", "UP")
        .log("system.status.served");
    return new StatusResponse(application, "UP", version);
  }
}
