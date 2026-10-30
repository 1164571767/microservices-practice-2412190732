package com.zjgsu.sfy.integration.controller;

import com.zjgsu.sfy.identity.*;
import com.zjgsu.sfy.integration.*;
import com.zjgsu.sfy.medical.*;
import com.zjgsu.sfy.owner.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class HandoffInternalController {

  private final ConsultationHandoffLookup.Pharmacy api;

  public HandoffInternalController(ConsultationHandoffLookup.Pharmacy api) {
    this.api = api;
  }

  @GetMapping("/internal/dispenses/by-source/{id}")
  public ConsultationHandoffLookup.DispenseProgress progress(@PathVariable UUID id) {
    return api.findDispense(id);
  }
}
