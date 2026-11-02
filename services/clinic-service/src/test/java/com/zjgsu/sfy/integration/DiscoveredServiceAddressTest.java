package com.zjgsu.sfy.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sun.net.httpserver.HttpServer;
import com.zjgsu.sfy.common.error.BusinessException;
import com.zjgsu.sfy.common.error.ErrorCode;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import tools.jackson.databind.json.JsonMapper;

class DiscoveredServiceAddressTest {
  private final DiscoveryClient discovery = mock(DiscoveryClient.class);
  private final DiscoveredServiceAddress addresses = new DiscoveredServiceAddress(discovery);

  @Test
  void emptyListFailsClosedWith503() {
    when(discovery.getInstances("patient-service")).thenReturn(List.of());
    var error = assertThrows(BusinessException.class, addresses::patient);
    assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, error.errorCode());
    assertEquals(503, error.errorCode().status().value());
  }

  @Test
  void discoveryExceptionIs503() {
    when(discovery.getInstances("patient-service")).thenThrow(new IllegalStateException("offline"));
    assertEquals(
        ErrorCode.DEPENDENCY_UNAVAILABLE,
        assertThrows(BusinessException.class, addresses::patient).errorCode());
  }

  @Test
  void eachBusinessCallUsesTheCurrentDiscoveredInstanceAndOriginalPath() throws Exception {
    var first = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var second = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var calls = new AtomicInteger();
    var pet = UUID.randomUUID();
    var path = "/internal/patients/pets/" + pet + "/admission";
    for (var server : List.of(first, second)) {
      server.createContext(
          path,
          request -> {
            assertEquals("POST", request.getRequestMethod());
            assertEquals(
                "test-service-token-at-least-32-characters",
                request.getRequestHeaders().getFirst("X-Service-Token"));
            request.getRequestBody().readAllBytes();
            calls.incrementAndGet();
            request.sendResponseHeaders(204, -1);
            request.close();
          });
      server.start();
    }
    try {
      when(discovery.getInstances("patient-service"))
          .thenReturn(
              List.of(
                  new DefaultServiceInstance(
                      "one", "patient-service", "127.0.0.1", first.getAddress().getPort(), false)))
          .thenReturn(List.of())
          .thenReturn(
              List.of(
                  new DefaultServiceInstance(
                      "two",
                      "patient-service",
                      "127.0.0.1",
                      second.getAddress().getPort(),
                      false)));
      var client =
          new PatientHttpClient(
              new HttpTransport(
                  JsonMapper.builder().findAndAddModules().build(),
                  "test-service-token-at-least-32-characters",
                  Duration.ofSeconds(1),
                  Duration.ofSeconds(1)),
              addresses);
      client.requireActivePetForBooking(pet);
      assertThrows(BusinessException.class, () -> client.requireActivePetForBooking(pet));
      client.requireActivePetForBooking(pet);
      assertEquals(2, calls.get());
      verify(discovery, times(3)).getInstances("patient-service");
    } finally {
      first.stop(0);
      second.stop(0);
    }
  }
}
