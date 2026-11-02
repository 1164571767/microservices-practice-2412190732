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
  @Autowired AppointmentRepository appointments;
  @MockitoBean DiscoveryClient discovery;

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
          .thenReturn(
              List.of(
                  new DefaultServiceInstance(
                      "test",
                      "patient-service",
                      "127.0.0.1",
                      server.getAddress().getPort(),
                      false)))
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
}
