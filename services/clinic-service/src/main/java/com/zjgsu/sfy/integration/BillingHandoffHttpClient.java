package com.zjgsu.sfy.integration;

import com.zjgsu.sfy.identity.*;
import com.zjgsu.sfy.medical.*;
import com.zjgsu.sfy.owner.*;
import java.util.*;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Component;

@Component
public class BillingHandoffHttpClient implements ConsultationHandoffLookup.Billing {
  private final HttpTransport http;
  private final PeerProperties peers;

  public BillingHandoffHttpClient(HttpTransport http, PeerProperties peers) {
    this.http = http;
    this.peers = peers;
  }

  public ConsultationHandoffLookup.BillProgress findBill(UUID id) {
    return http.get(
        peers.getBillingUrl() + "/internal/bills/by-source/" + id,
        new ParameterizedTypeReference<ConsultationHandoffLookup.BillProgress>() {});
  }
}
