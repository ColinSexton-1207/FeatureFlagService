package com.example.flagship.environment.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "environments")
public class EnvironmentEntity {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(nullable = false, updatable = false)
    private String key;

    @Column(nullable = false)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder;

    protected EnvironmentEntity() {}

    public UUID getId() { return id; }

    public UUID getProjectId() { return projectId; }

    public String getKey() { return key; }

    public String getName() { return name; }

    public short getSortOrder() { return sortOrder; }
}
