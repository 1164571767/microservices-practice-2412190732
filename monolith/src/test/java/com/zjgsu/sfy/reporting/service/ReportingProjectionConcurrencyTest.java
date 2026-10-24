package com.zjgsu.sfy.reporting.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.zjgsu.sfy.billing.BillPaid;
import com.zjgsu.sfy.reporting.repository.RevenueFactRepository;
import com.zjgsu.sfy.reporting.support.ReportingIntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

/** 并发重复投递同一事件时，统计事实表的唯一键保证只有一条落库。 */
class ReportingProjectionConcurrencyTest extends ReportingIntegrationTest {

  private static final int THREADS = 6;

  @Autowired ApplicationEventPublisher eventPublisher;
  @Autowired RevenueFactRepository revenueFactRepository;

  @Test
  void concurrentDuplicateBillPaidCreatesSingleFact() throws Exception {
    UUID billId = UUID.randomUUID();
    BillPaid event =
        new BillPaid(
            billId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            new BigDecimal("88.00"),
            "CNY",
            UUID.randomUUID(),
            Instant.now());
    ExecutorService pool = Executors.newFixedThreadPool(THREADS);
    CountDownLatch ready = new CountDownLatch(THREADS);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<?>> futures = new ArrayList<>();
    try {
      for (int i = 0; i < THREADS; i++) {
        futures.add(
            pool.submit(
                () -> {
                  ready.countDown();
                  start.await();
                  eventPublisher.publishEvent(event);
                  return null;
                }));
      }
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      for (Future<?> future : futures) {
        future.get(15, TimeUnit.SECONDS);
      }
    } finally {
      pool.shutdownNow();
    }

    assertThat(
            revenueFactRepository.findAll().stream()
                .filter(fact -> fact.getBillId().equals(billId)))
        .hasSize(1);
  }
}
