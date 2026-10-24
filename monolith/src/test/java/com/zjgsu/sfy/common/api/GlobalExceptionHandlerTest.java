package com.zjgsu.sfy.common.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ErrorProbeController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerTest {

  @Autowired MockMvc mockMvc;

  @Test
  void businessExceptionIsMappedToItsCode() throws Exception {
    mockMvc
        .perform(get("/test/errors/missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
  }

  @Test
  void invalidRequestBodyIsMappedToValidationFailed() throws Exception {
    mockMvc
        .perform(
            post("/test/errors/validate").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void missingRequiredParameterIsMappedToValidationFailed() throws Exception {
    mockMvc
        .perform(get("/test/errors/require-param"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void unmappedPathIsMappedToResourceNotFound() throws Exception {
    mockMvc
        .perform(get("/definitely-not-mapped"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
  }

  @Test
  void optimisticLockConflictIsMappedToConcurrentModification() throws Exception {
    mockMvc
        .perform(get("/test/errors/concurrent"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
  }

  @Test
  void unexpectedExceptionIsMappedToInternalError() throws Exception {
    mockMvc
        .perform(get("/test/errors/boom"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
  }
}
