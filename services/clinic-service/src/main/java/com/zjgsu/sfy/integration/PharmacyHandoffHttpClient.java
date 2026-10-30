package com.zjgsu.sfy.integration;

import com.zjgsu.sfy.identity.*;
import com.zjgsu.sfy.medical.*;
import com.zjgsu.sfy.owner.*;
import java.util.*;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Component;

@Component
public class PharmacyHandoffHttpClient implements ConsultationHandoffLookup.Pharmacy {
  private final HttpTransport http;
  private final PeerProperties peers;

  public PharmacyHandoffHttpClient(HttpTransport http, PeerProperties peers) {
    this.http = http;
    this.peers = peers;
  }

  public ConsultationHandoffLookup.DispenseProgress findDispense(UUID id) {
    return http.get(
        peers.getPharmacyUrl() + "/internal/dispenses/by-source/" + id,
        new ParameterizedTypeReference<ConsultationHandoffLookup.DispenseProgress>() {});
  }
}
