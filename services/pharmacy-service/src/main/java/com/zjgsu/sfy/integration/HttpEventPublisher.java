package com.zjgsu.sfy.integration;

import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

@Component
public class HttpEventPublisher {
  private final HttpTransport http;
  private final PeerProperties peers;

  public HttpEventPublisher(HttpTransport http, PeerProperties peers) {
    this.http = http;
    this.peers = peers;
  }

  private void send(String target, String type, Object event) {
    try {
      http.post(
          target + "/internal/events/" + type, event, new ParameterizedTypeReference<Void>() {});
    } catch (RuntimeException ex) {
      LoggerFactory.getLogger(getClass())
          .error(
              "event.delivery_failed type={} target={} event={} (no automatic retry)",
              type,
              target,
              event,
              ex);
    }
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onLowStockDetected(com.zjgsu.sfy.pharmacy.LowStockDetected event) {
    send(peers.getNotificationUrl(), "LowStockDetected", event);
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onPrescriptionDispensed(com.zjgsu.sfy.pharmacy.PrescriptionDispensed event) {
    send(peers.getReportingUrl(), "PrescriptionDispensed", event);
  }
}
