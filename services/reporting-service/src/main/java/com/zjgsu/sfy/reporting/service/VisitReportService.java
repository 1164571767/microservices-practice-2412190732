package com.zjgsu.sfy.reporting.service;

import com.zjgsu.sfy.common.error.BusinessException;
import com.zjgsu.sfy.common.error.ErrorCode;
import com.zjgsu.sfy.identity.CurrentUser;
import com.zjgsu.sfy.identity.IdentityApi;
import com.zjgsu.sfy.identity.UserProfile;
import com.zjgsu.sfy.reporting.common.ReportingProperties;
import com.zjgsu.sfy.reporting.dto.resp.VisitReportResponse;
import com.zjgsu.sfy.reporting.repository.VisitFactRepository;
import com.zjgsu.sfy.reporting.service.result.ReportPeriod;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 门诊量与医生工作量查询：只读聚合 statistics 事实表，经 identity 只读解析医生姓名。 */
@Service
public class VisitReportService {

  private final VisitFactRepository visitFactRepository;
  private final IdentityApi identityApi;
  private final ReportingProperties properties;

  public VisitReportService(
      VisitFactRepository visitFactRepository,
      IdentityApi identityApi,
      ReportingProperties properties) {
    this.visitFactRepository = visitFactRepository;
    this.identityApi = identityApi;
    this.properties = properties;
  }

  @Transactional(readOnly = true)
  public VisitReportResponse report(
      CurrentUser requester, LocalDate from, LocalDate to, UUID doctorId) {
    requireAdmin(requester);
    ReportPeriod period = ReportPeriod.of(from, to, properties.maxRangeDays());
    long totalVisits = totalVisits(period, doctorId);
    List<VisitReportResponse.DailyVisit> daily =
        dailyVisits(period, doctorId).stream()
            .map(row -> new VisitReportResponse.DailyVisit(row.getStatDate(), row.getVisits()))
            .toList();
    return new VisitReportResponse(
        period.from(),
        period.to(),
        totalVisits,
        daily,
        doctorWorkloads(period, doctorId, totalVisits));
  }

  private long totalVisits(ReportPeriod period, UUID doctorId) {
    return doctorId == null
        ? visitFactRepository.countByStatDateBetween(period.from(), period.to())
        : visitFactRepository.countByStatDateBetweenAndDoctorId(
            period.from(), period.to(), doctorId);
  }

  private List<VisitFactRepository.DailyVisits> dailyVisits(ReportPeriod period, UUID doctorId) {
    return doctorId == null
        ? visitFactRepository.countDaily(period.from(), period.to())
        : visitFactRepository.countDailyByDoctor(period.from(), period.to(), doctorId);
  }

  private List<VisitReportResponse.DoctorWorkload> doctorWorkloads(
      ReportPeriod period, UUID doctorId, long totalVisits) {
    if (doctorId != null) {
      return totalVisits == 0
          ? List.of()
          : List.of(
              new VisitReportResponse.DoctorWorkload(doctorId, displayName(doctorId), totalVisits));
    }
    return visitFactRepository.countByDoctor(period.from(), period.to()).stream()
        .map(
            row ->
                new VisitReportResponse.DoctorWorkload(
                    row.getDoctorId(), displayName(row.getDoctorId()), row.getVisits()))
        .toList();
  }

  private String displayName(UUID doctorId) {
    return identityApi.findUser(doctorId).map(UserProfile::displayName).orElse(null);
  }

  private static void requireAdmin(CurrentUser requester) {
    if (!requester.isAdmin()) {
      throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
    }
  }
}
