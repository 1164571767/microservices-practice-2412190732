package com.zjgsu.sfy.system.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SystemStatusController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(SystemStatusControllerTest.TestMetricsConfiguration.class)
class SystemStatusControllerTest {

  @Autowired MockMvc mockMvc;

  @Autowired MeterRegistry meterRegistry;

  @Test
  void statusReturnsUpWithApplicationInfo() throws Exception {
    mockMvc
        .perform(get("/api/system/status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.application").value("monolith"))
        .andExpect(jsonPath("$.status").value("UP"))
        .andExpect(jsonPath("$.version").exists());
  }

  @Test
  void statusIncrementsRequestCounter() throws Exception {
    mockMvc.perform(get("/api/system/status")).andExpect(status().isOk());

    assertTrue(meterRegistry.get("petcare.system.status.requests").counter().count() > 0);
  }

  @TestConfiguration
  static class TestMetricsConfiguration {

    @Bean
    @Primary
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }
  }
}
