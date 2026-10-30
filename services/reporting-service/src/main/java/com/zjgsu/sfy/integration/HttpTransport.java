package com.zjgsu.sfy.integration;

import com.zjgsu.sfy.common.error.*;
import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import tools.jackson.databind.ObjectMapper;

/** Bounded HTTP calls; failures never become an empty successful validation. */
@Component
public class HttpTransport {
  private final RestTemplate rest;
  private final String token;
  private final ObjectMapper mapper;

  public HttpTransport(
      tools.jackson.databind.json.JsonMapper mapper,
      @Value("${services.token}") String token,
      @Value("${services.connect-timeout}") Duration connectTimeout,
      @Value("${services.read-timeout}") Duration readTimeout) {
    if (token == null || token.length() < 32)
      throw new IllegalStateException("SERVICE_TOKEN must contain at least 32 characters");
    if (connectTimeout.isNegative()
        || connectTimeout.isZero()
        || readTimeout.isNegative()
        || readTimeout.isZero()) throw new IllegalStateException("HTTP timeouts must be positive");
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(connectTimeout);
    factory.setReadTimeout(readTimeout);
    this.rest = new RestTemplate(factory);
    this.rest.getMessageConverters().removeIf(c -> c instanceof JacksonJsonHttpMessageConverter);
    this.rest.getMessageConverters().add(new JacksonJsonHttpMessageConverter(mapper));
    this.token = token;
    this.mapper = mapper;
  }

  public <T> T get(String url, ParameterizedTypeReference<T> type) {
    return call(url, HttpMethod.GET, null, type);
  }

  public <T> T post(String url, Object body, ParameterizedTypeReference<T> type) {
    return call(url, HttpMethod.POST, body, type);
  }

  public <T> T required(T value) {
    if (value == null) throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE, "依赖服务返回空响应");
    return value;
  }

  private <T> T call(
      String url, HttpMethod method, Object body, ParameterizedTypeReference<T> type) {
    var headers = new HttpHeaders();
    headers.set("X-Service-Token", token);
    headers.setContentType(MediaType.APPLICATION_JSON);
    var authentication =
        org.springframework.security.core.context.SecurityContextHolder.getContext()
            .getAuthentication();
    if (authentication != null
        && authentication.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt jwt)
      headers.setBearerAuth(jwt.getTokenValue());
    try {
      return rest.exchange(URI.create(url), method, new HttpEntity<>(body, headers), type)
          .getBody();
    } catch (HttpClientErrorException ex) {
      if (ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403)
        throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE, "服务间认证失败");
      try {
        var error = mapper.readTree(ex.getResponseBodyAsString());
        var code = ErrorCode.valueOf(error.path("code").asText());
        if (code.status().value() != ex.getStatusCode().value())
          throw new IllegalArgumentException();
        throw new BusinessException(code, error.path("message").asText(code.defaultMessage()));
      } catch (BusinessException known) {
        throw known;
      } catch (RuntimeException malformed) {
        throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE, "依赖服务返回无效错误");
      }
    } catch (RestClientException ex) {
      throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE, "依赖服务连接失败、超时或返回无效响应");
    }
  }
}
