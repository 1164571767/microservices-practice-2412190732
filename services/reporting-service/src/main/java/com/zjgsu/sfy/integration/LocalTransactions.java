package com.zjgsu.sfy.integration;

import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

/** Only local locks and writes run in this READ_COMMITTED transaction. */
@Component
public class LocalTransactions {
  private final TransactionTemplate template;

  public LocalTransactions(PlatformTransactionManager manager) {
    template = new TransactionTemplate(manager);
    template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  public <T> T write(Supplier<T> action) {
    return template.execute(status -> action.get());
  }
}
