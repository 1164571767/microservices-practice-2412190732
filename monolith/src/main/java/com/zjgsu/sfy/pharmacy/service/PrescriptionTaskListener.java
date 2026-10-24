package com.zjgsu.sfy.pharmacy.service;

import com.zjgsu.sfy.medical.PrescriptionIssued;
import com.zjgsu.sfy.pharmacy.entity.PrescriptionTask;
import com.zjgsu.sfy.pharmacy.repository.PrescriptionTaskRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

/** 与开方同事务；任何回滚均不留下虚假待发任务。 */
@Component
public class PrescriptionTaskListener {
  private final PrescriptionTaskRepository tasks;

  public PrescriptionTaskListener(PrescriptionTaskRepository tasks) {
    this.tasks = tasks;
  }

  @EventListener
  @Transactional(propagation = Propagation.MANDATORY)
  public void issued(PrescriptionIssued event) {
    if (!tasks.existsById(event.prescriptionId())) tasks.save(PrescriptionTask.from(event));
  }
}
