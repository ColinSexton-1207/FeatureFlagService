package com.example.flagship.project.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "projects")
public class ProjectEntity {
    public static final String DEFAULT_KEY = "default";

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private String key;

    @Column(nullable = false)
    private String name;

    protected ProjectEntity() {}

    public UUID getId() { return id; }

    public String getKey() { return key; }

    public String getName() { return name; }
}
