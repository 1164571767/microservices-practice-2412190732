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
  public void onAppointmentBooked(com.zjgsu.sfy.appointment.AppointmentBooked event) {
    send(peers.getNotificationUrl(), "AppointmentBooked", event);
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onQueueNumberCalled(com.zjgsu.sfy.appointment.QueueNumberCalled event) {
    send(peers.getNotificationUrl(), "QueueNumberCalled", event);
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onMedicalRecordCreated(com.zjgsu.sfy.medical.MedicalRecordCreated event) {
    send(peers.getReportingUrl(), "MedicalRecordCreated", event);
  }
}
