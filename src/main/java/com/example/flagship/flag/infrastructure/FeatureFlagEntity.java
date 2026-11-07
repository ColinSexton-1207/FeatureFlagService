package com.example.flagship.flag.infrastructure;

import com.example.flagship.flag.domain.FlagType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "feature_flags")
public class FeatureFlagEntity {
    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(nullable = false, updatable = false)
    private String key;

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "flag_type", nullable = false, updatable = false)
    private FlagType flagType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @Column(name = "archived_at")
    private Instant archivedAt;

    protected FeatureFlagEntity() {}

    public FeatureFlagEntity(UUID projectId, String key, String name, String description,
                             FlagType flagType, String createdBy, Instant now) {
        this.projectId = projectId;
        this.key = key;
        this.name = name;
        this.description = description;
        this.flagType = flagType;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updateMetadata(String name, String description, Instant now) {
        this.name = name;
        this.description = description;
        this.updatedAt = now;
    }

    /** Marks the flag archived. Archiving is one-way in v1. */
    public void archive(Instant now) {
        this.archivedAt = now;
        this.updatedAt = now;
    }

    public boolean isArchived() { return archivedAt != null; }

    public UUID getId() { return id; }

    public UUID getProjectId() { return projectId; }

    public String getKey() { return key; }

    public String getName() { return name; }

    public String getDescription() { return description; }

    public FlagType getFlagType() { return flagType; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }

    public String getCreatedBy() { return createdBy; }

    public Instant getArchivedAt() { return archivedAt; }
}
