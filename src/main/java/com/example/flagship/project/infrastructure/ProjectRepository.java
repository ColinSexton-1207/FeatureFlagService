package com.example.flagship.project.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<ProjectEntity, UUID> {
    Optional<ProjectEntity> findByKey(String key);
}
