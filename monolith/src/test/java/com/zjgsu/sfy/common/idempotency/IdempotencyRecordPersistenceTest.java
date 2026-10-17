package com.zjgsu.sfy.common.idempotency;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class IdempotencyRecordPersistenceTest {

  @Autowired IdempotencyRecordRepository repository;

  @Test
  void persistsRecordAndEnforcesUniqueKey() {
    String key = "it-" + UUID.randomUUID();

    repository.saveAndFlush(new IdempotencyRecord(key, "hash-a"));
    assertTrue(repository.findByIdempotencyKey(key).isPresent());

    assertThrows(
        DataIntegrityViolationException.class,
        () -> repository.saveAndFlush(new IdempotencyRecord(key, "hash-b")));
  }
}
