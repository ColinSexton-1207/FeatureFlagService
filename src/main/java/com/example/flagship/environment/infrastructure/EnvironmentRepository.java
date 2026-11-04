package com.example.flagship.environment.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnvironmentRepository extends JpaRepository<EnvironmentEntity, UUID> {

    List<EnvironmentEntity> findByProjectIdOrderBySortOrder(UUID projectId);
}
