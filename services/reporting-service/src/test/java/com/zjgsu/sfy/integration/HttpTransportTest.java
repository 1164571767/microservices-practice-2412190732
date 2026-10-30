package com.zjgsu.sfy.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import com.zjgsu.sfy.common.error.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.core.ParameterizedTypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Real loopback HTTP: exercises RestTemplate serialization, credentials and fail-closed errors. */
class HttpTransportTest {
  private HttpServer server;
  private HttpTransport http;
  private String base;
  private final AtomicReference<String> token = new AtomicReference<>();
  private static final String SECRET = "test-only-service-token-at-least-32-characters";

  record Probe(UUID id, Instant at) {}

  @BeforeEach
  void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    http =
        new HttpTransport(
            JsonMapper.builder().findAndAddModules().build(),
            SECRET,
            Duration.ofSeconds(1),
            Duration.ofSeconds(1));
    base = "http://127.0.0.1:" + server.getAddress().getPort();
    server.start();
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  private void endpoint(int status, String response) {
    server.createContext(
        "/probe",
        request -> {
          token.set(request.getRequestHeaders().getFirst("X-Service-Token"));
          request.getRequestBody().readAllBytes();
          request.getResponseHeaders().set("Content-Type", "application/json");
          byte[] body = response.getBytes(StandardCharsets.UTF_8);
          request.sendResponseHeaders(status, body.length);
          request.getResponseBody().write(body);
          request.close();
        });
  }

  @Test
  void sendsServiceCredentialsAndReadsTypedUuidAndTime() {
    UUID id = UUID.randomUUID();
    Instant at = Instant.parse("2026-10-26T00:00:00Z");
    endpoint(200, "{\"id\":\"" + id + "\",\"at\":\"" + at + "\"}");
    Probe result =
        http.post(
            base + "/probe",
            Map.of("ids", List.of(id)),
            new ParameterizedTypeReference<Probe>() {});
    assertEquals(new Probe(id, at), result);
    assertEquals(SECRET, token.get());
  }

  @Test
  void missingDependencyResourceRemains404() {
    endpoint(404, "{\"code\":\"RESOURCE_NOT_FOUND\",\"message\":\"missing pet\"}");
    var ex =
        assertThrows(
            BusinessException.class,
            () -> http.get(base + "/probe", new ParameterizedTypeReference<Probe>() {}));
    assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.errorCode());
  }

  @Test
  void serverFailureIsUnavailableRatherThanEmptyData() {
    endpoint(500, "{\"code\":\"INTERNAL_ERROR\"}");
    assertUnavailable();
  }

  @Test
  void malformedSuccessIsUnavailable() {
    endpoint(200, "not-json");
    assertUnavailable();
  }

  @Test
  void malformedErrorIsUnavailable() {
    endpoint(404, "not-json");
    assertUnavailable();
  }

  @Test
  void invalidServiceCredentialsAreDependencyFailure() {
    endpoint(401, "{}");
    assertUnavailable();
  }

  @Test
  void absentRequiredResponseIsUnavailable() {
    assertEquals(
        ErrorCode.DEPENDENCY_UNAVAILABLE,
        assertThrows(BusinessException.class, () -> http.required(null)).errorCode());
  }

  @Test
  void connectionRefusalIsUnavailable() {
    server.stop(0);
    assertUnavailable();
  }

  @Test
  void readTimeoutIsBounded() {
    server.createContext(
        "/probe",
        request -> {
          try {
            Thread.sleep(300);
          } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
          }
          request.close();
        });
    http =
        new HttpTransport(
            JsonMapper.builder().build(), SECRET, Duration.ofMillis(100), Duration.ofMillis(50));
    long started = System.nanoTime();
    assertUnavailable();
    assertTrue(Duration.ofNanos(System.nanoTime() - started).toMillis() < 2000);
  }

  @Test
  void encodedUnicodeQueryIsNotEncodedTwice() {
    var query = new java.util.concurrent.atomic.AtomicReference<String>();
    server.createContext(
        "/search",
        request -> {
          query.set(request.getRequestURI().getRawQuery());
          byte[] body = "[]".getBytes(StandardCharsets.UTF_8);
          request.getResponseHeaders().set("Content-Type", "application/json");
          request.sendResponseHeaders(200, body.length);
          request.getResponseBody().write(body);
          request.close();
        });
    String url =
        org.springframework.web.util.UriComponentsBuilder.fromUriString(base + "/search")
            .queryParam("query", "宠物 主人")
            .build()
            .encode()
            .toUriString();
    http.get(url, new ParameterizedTypeReference<List<String>>() {});
    assertEquals("query=宠物 主人", java.net.URLDecoder.decode(query.get(), StandardCharsets.UTF_8));
  }

  private void assertUnavailable() {
    assertEquals(
        ErrorCode.DEPENDENCY_UNAVAILABLE,
        assertThrows(
                BusinessException.class,
                () -> http.get(base + "/probe", new ParameterizedTypeReference<Probe>() {}))
            .errorCode());
  }
}
