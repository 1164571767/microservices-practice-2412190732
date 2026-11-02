package com.zjgsu.sfy.integration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "services")
public class PeerProperties {
  private String identityUrl, clinicUrl, pharmacyUrl, billingUrl, notificationUrl, reportingUrl;

  public String getIdentityUrl() {
    return identityUrl;
  }

  public void setIdentityUrl(String value) {
    identityUrl = value;
  }

  public String getClinicUrl() {
    return clinicUrl;
  }

  public void setClinicUrl(String value) {
    clinicUrl = value;
  }

  public String getPharmacyUrl() {
    return pharmacyUrl;
  }

  public void setPharmacyUrl(String value) {
    pharmacyUrl = value;
  }

  public String getBillingUrl() {
    return billingUrl;
  }

  public void setBillingUrl(String value) {
    billingUrl = value;
  }

  public String getNotificationUrl() {
    return notificationUrl;
  }

  public void setNotificationUrl(String value) {
    notificationUrl = value;
  }

  public String getReportingUrl() {
    return reportingUrl;
  }

  public void setReportingUrl(String value) {
    reportingUrl = value;
  }
}
