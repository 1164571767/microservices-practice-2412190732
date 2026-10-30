package com.zjgsu.sfy.common.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "idempotency_record")
public class IdempotencyRecord {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
  private String idempotencyKey;

  @Column(name = "request_hash", nullable = false, length = 64)
  private String requestHash;

  /** 该幂等键对应的业务资源标识，用于重试时回放同一结果。 */
  @Column(name = "result_ref")
  private UUID resultRef;

  protected IdempotencyRecord() {}

  public IdempotencyRecord(String idempotencyKey, String requestHash) {
    this(idempotencyKey, requestHash, null);
  }

  public IdempotencyRecord(String idempotencyKey, String requestHash, UUID resultRef) {
    this.idempotencyKey = idempotencyKey;
    this.requestHash = requestHash;
    this.resultRef = resultRef;
  }

  public UUID getId() {
    return id;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public String getRequestHash() {
    return requestHash;
  }

  public UUID getResultRef() {
    return resultRef;
  }
}
