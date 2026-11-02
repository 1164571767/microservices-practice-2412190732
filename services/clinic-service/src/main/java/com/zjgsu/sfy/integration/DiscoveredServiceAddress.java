package com.zjgsu.sfy.integration;

import com.zjgsu.sfy.common.error.BusinessException;
import com.zjgsu.sfy.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;

/** Resolves the current SDK instance list on every call; never falls back to a fixed URL. */
@Component
public class DiscoveredServiceAddress {
  private static final Logger log = LoggerFactory.getLogger(DiscoveredServiceAddress.class);
  private final DiscoveryClient discovery;

  public DiscoveredServiceAddress(DiscoveryClient discovery) {
    this.discovery = discovery;
  }

  public String patient() {
    try {
      var instances = discovery.getInstances("patient-service");
      if (instances.isEmpty()) {
        throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE, "患者档案服务暂无可用实例");
      }
      var uri = instances.getFirst().getUri();
      if (!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme())) {
        throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE, "患者档案实例地址无效");
      }
      log.info("Discovered service=patient-service uri={}", uri);
      return uri.toString();
    } catch (BusinessException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE, "患者档案服务发现失败");
    }
  }
}
