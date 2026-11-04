package com.example.flagship.flag.application;

import com.example.flagship.project.infrastructure.ProjectEntity;
import com.example.flagship.project.infrastructure.ProjectRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * v1 serves a single seeded project. Keeping the lookup in one place means multi-project
 * support later changes this class, not every use case.
 */
@Component
class DefaultProjectResolver {

    private final ProjectRepository projects;

    DefaultProjectResolver(ProjectRepository projects) {
        this.projects = projects;
    }

    UUID defaultProjectId() {
        return projects.findByKey(ProjectEntity.DEFAULT_KEY)
                .map(ProjectEntity::getId)
                .orElseThrow(() -> new IllegalStateException(
                        "Default project is missing; the V1 migration has not been applied"));
    }
}
