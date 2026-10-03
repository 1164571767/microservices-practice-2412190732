package com.zjgsu.sfy.system.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SystemStatusController.class)
class SystemStatusControllerTest {

  @Autowired MockMvc mockMvc;

  @Test
  void statusReturnsUpWithApplicationInfo() throws Exception {
    mockMvc
        .perform(get("/api/system/status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.application").value("monolith"))
        .andExpect(jsonPath("$.status").value("UP"))
        .andExpect(jsonPath("$.version").exists());
  }
}
