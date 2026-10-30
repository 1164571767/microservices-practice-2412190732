package com.zjgsu.sfy.pharmacy.service;

import com.zjgsu.sfy.common.error.BusinessException;
import com.zjgsu.sfy.common.error.ErrorCode;
import com.zjgsu.sfy.common.idempotency.IdempotencyKeys;
import com.zjgsu.sfy.common.idempotency.IdempotencyRecordRepository;
import com.zjgsu.sfy.common.idempotency.RequestHashes;
import com.zjgsu.sfy.identity.CurrentUser;
import com.zjgsu.sfy.pharmacy.service.result.DispenseDetail;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 发药用例编排。
 *
 * <p>本方法本身不开启事务：写侧在 {@link DispenseWriter} 的独立事务中完成；若并发重复请求触发唯一键冲突，
 * 事务已回滚，这里在新事务中读取已提交的赢家并回放同一结果（幂等）。
 */
@Service
public class DispenseService {

  private final DispenseWriter dispenseWriter;
  private final DispenseQueryService queryService;
  private final IdempotencyRecordRepository idempotencyRepository;

  public DispenseService(
      DispenseWriter dispenseWriter,
      DispenseQueryService queryService,
      IdempotencyRecordRepository idempotencyRepository) {
    this.dispenseWriter = dispenseWriter;
    this.queryService = queryService;
    this.idempotencyRepository = idempotencyRepository;
  }

  public DispenseDetail dispense(
      CurrentUser requester, UUID prescriptionId, String idempotencyKey) {
    requirePharmacyStaff(requester);
    String key = IdempotencyKeys.normalize(idempotencyKey);
    String requestHash = RequestHashes.sha256(prescriptionId.toString());

    if (key != null) {
      Optional<DispenseDetail> replay = replayByIdempotencyKey(key, requestHash);
      if (replay.isPresent()) {
        return replay.get();
      }
    }
    Optional<DispenseDetail> alreadyDispensed = queryService.findByPrescriptionId(prescriptionId);
    if (alreadyDispensed.isPresent()) {
      return alreadyDispensed.get();
    }

    try {
      return dispenseWriter.write(requester, prescriptionId, requestHash, key);
    } catch (DataIntegrityViolationException ex) {
      if (key != null) {
        Optional<DispenseDetail> replay = replayByIdempotencyKey(key, requestHash);
        if (replay.isPresent()) {
          return replay.get();
        }
      }
      Optional<DispenseDetail> winner = queryService.findByPrescriptionId(prescriptionId);
      if (winner.isPresent()) {
        return winner.get();
      }
      throw ex;
    }
  }

  private Optional<DispenseDetail> replayByIdempotencyKey(String key, String requestHash) {
    return idempotencyRepository
        .findByIdempotencyKey(key)
        .map(
            record -> {
              if (!record.getRequestHash().equals(requestHash)) {
                throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
              }
              return queryService
                  .findById(record.getResultRef())
                  .orElseThrow(
                      () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "发药记录不存在"));
            });
  }

  private static void requirePharmacyStaff(CurrentUser requester) {
    if (!requester.isPharmacyStaff()) {
      throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
    }
  }
}
