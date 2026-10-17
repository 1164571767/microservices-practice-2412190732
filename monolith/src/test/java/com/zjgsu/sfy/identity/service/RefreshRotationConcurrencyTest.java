package com.zjgsu.sfy.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.zjgsu.sfy.common.error.BusinessException;
import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.entity.User;
import com.zjgsu.sfy.identity.support.IdentityIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RefreshRotationConcurrencyTest extends IdentityIntegrationTest {

  private static final int ATTEMPTS = 8;

  @Autowired AuthService authService;

  /** 同一 refresh token 被并发消费时，只有一次轮换成功，其余按 token 重放被拒绝。 */
  @Test
  void concurrentRefreshSucceedsExactlyOnce() throws Exception {
    User user = seedUser(Set.of(Role.OWNER));
    String refreshToken = loginRefreshToken(user.getUsername());

    ExecutorService pool = Executors.newFixedThreadPool(ATTEMPTS);
    CyclicBarrier barrier = new CyclicBarrier(ATTEMPTS);
    List<Future<Boolean>> results = new ArrayList<>();
    try {
      for (int i = 0; i < ATTEMPTS; i++) {
        results.add(pool.submit(rotateOnce(barrier, refreshToken)));
      }
      int successes = 0;
      for (Future<Boolean> result : results) {
        if (result.get(30, TimeUnit.SECONDS)) {
          successes++;
        }
      }
      assertThat(successes).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }

  private Callable<Boolean> rotateOnce(CyclicBarrier barrier, String refreshToken) {
    return () -> {
      barrier.await(30, TimeUnit.SECONDS);
      try {
        authService.refresh(refreshToken);
        return true;
      } catch (BusinessException ex) {
        return false;
      }
    };
  }
}
