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

  private final ConsultationHandoffLookup.Billing api;

  public HandoffInternalController(ConsultationHandoffLookup.Billing api) {
    this.api = api;
  }

  @GetMapping("/internal/bills/by-source/{id}")
  public ConsultationHandoffLookup.BillProgress progress(@PathVariable UUID id) {
    return api.findBill(id);
  }
}
