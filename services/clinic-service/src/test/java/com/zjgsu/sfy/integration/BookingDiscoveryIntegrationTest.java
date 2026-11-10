package com.zjgsu.sfy.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sun.net.httpserver.HttpServer;
import com.zjgsu.sfy.appointment.entity.Appointment;
import com.zjgsu.sfy.appointment.repository.AppointmentRepository;
import com.zjgsu.sfy.appointment.service.AppointmentService;
import com.zjgsu.sfy.appointment.service.command.BookAppointmentCommand;
import com.zjgsu.sfy.common.error.BusinessException;
import com.zjgsu.sfy.common.error.ErrorCode;
import com.zjgsu.sfy.identity.CurrentUser;
import com.zjgsu.sfy.identity.common.Role;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Real local persistence and mocked discovery: failed admission must leave local data unchanged.
 */
@SpringBootTest
class BookingDiscoveryIntegrationTest {
  @Autowired AppointmentService service;
  @Autowired PatientHttpClient patient;
  @Autowired AppointmentRepository appointments;
  @MockitoBean DiscoveryClient discovery;

  @Test
  void admissionUsesTheDiscoveredHttpEndpointAndPreservesBusinessRejections() throws Exception {
    var pet = UUID.randomUUID();
    var requests = new java.util.concurrent.atomic.AtomicInteger();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/internal/patients/pets/" + pet + "/admission",
        request -> {
          assertEquals("POST", request.getRequestMethod());
          assertEquals(
              "test-only-internal-secret-of-at-least-32-characters",
              request.getRequestHeaders().getFirst("X-Service-Token"));
          if (requests.getAndIncrement() == 0) {
            request.sendResponseHeaders(204, -1);
          } else {
            byte[] body =
                "{\"code\":\"OPERATION_NOT_ALLOWED\",\"message\":\"archived\"}"
                    .getBytes(StandardCharsets.UTF_8);
            request.getResponseHeaders().set("Content-Type", "application/json");
            request.sendResponseHeaders(409, body.length);
            request.getResponseBody().write(body);
          }
          request.close();
        });
    server.start();
    try {
      when(discovery.getInstances("patient-service")).thenReturn(List.of(instance(server)));
      patient.requireActivePetForBooking(pet);
      var error =
          assertThrows(BusinessException.class, () -> patient.requireActivePetForBooking(pet));
      assertEquals(ErrorCode.OPERATION_NOT_ALLOWED, error.errorCode());
      assertEquals("archived", error.getMessage());
      assertEquals(2, requests.get());
      when(discovery.getInstances("patient-service")).thenReturn(List.of());
      assertEquals(
          ErrorCode.DEPENDENCY_UNAVAILABLE,
          assertThrows(BusinessException.class, () -> patient.requireActivePetForBooking(pet))
              .errorCode());
      when(discovery.getInstances("patient-service"))
          .thenThrow(new IllegalStateException("offline"));
      assertEquals(
          ErrorCode.DEPENDENCY_UNAVAILABLE,
          assertThrows(BusinessException.class, () -> patient.requireActivePetForBooking(pet))
              .errorCode());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void instanceDisappearingBetweenPetLookupAndAdmissionCannotSaveAnAppointment() throws Exception {
    var pet = UUID.randomUUID();
    var doctor = UUID.randomUUID();
    var start = Instant.now().plusSeconds(3600);
    var existing =
        appointments.saveAndFlush(Appointment.book(pet, doctor, start, start.plusSeconds(900)));
    long before = appointments.count();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/internal/patients/pets/" + pet,
        request -> {
          byte[] body =
              ("{\"id\":\""
                      + pet
                      + "\",\"ownerId\":\""
                      + UUID.randomUUID()
                      + "\",\"name\":\"pet\",\"species\":\"dog\"}")
                  .getBytes(StandardCharsets.UTF_8);
          request.getResponseHeaders().set("Content-Type", "application/json");
          request.sendResponseHeaders(200, body.length);
          request.getResponseBody().write(body);
          request.close();
        });
    server.start();
    try {
      when(discovery.getInstances("patient-service"))
          .thenReturn(List.of(instance(server)))
          .thenReturn(List.of());
      var admin = new CurrentUser(UUID.randomUUID(), "admin", Set.of(Role.ADMIN));
      var booking =
          new BookAppointmentCommand(pet, doctor, start.plusSeconds(900), start.plusSeconds(1800));
      var error =
          assertThrows(
              BusinessException.class, () -> service.book(admin, booking, "new-valid-booking"));
      assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, error.errorCode());
      assertEquals(before, appointments.count());
      assertTrue(appointments.existsById(existing.getId()));
      verify(discovery, times(2)).getInstances("patient-service");
    } finally {
      server.stop(0);
    }
  }

  private DefaultServiceInstance instance(HttpServer server) {
    return new DefaultServiceInstance(
        "test", "patient-service", "127.0.0.1", server.getAddress().getPort(), false);
  }
}
