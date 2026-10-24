package com.zjgsu.sfy.pharmacy.repository;

import com.zjgsu.sfy.pharmacy.entity.PrescriptionTask;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;

public interface PrescriptionTaskRepository
    extends JpaRepository<PrescriptionTask, UUID>, JpaSpecificationExecutor<PrescriptionTask> {}
