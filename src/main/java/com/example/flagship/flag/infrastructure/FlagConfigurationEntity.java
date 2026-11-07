package com.example.flagship.flag.infrastructure;

import com.example.flagship.flag.domain.FlagConfigurationRules;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "flag_configurations")
public class FlagConfigurationEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "flag_id", nullable = false, updatable = false)
    private UUID flagId;

    @Column(name = "environment_id", nullable = false, updatable = false)
    private UUID environmentId;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "rollout_percentage", nullable = false)
    private short rolloutPercentage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allow_users", nullable = false)
    private List<String> allowUsers = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "deny_users", nullable = false)
    private List<String> denyUsers = new ArrayList<>();

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    protected FlagConfigurationEntity() {}

    public static FlagConfigurationEntity initial(UUID flagId, UUID environmentId, String actor, Instant now) {
        FlagConfigurationEntity c = new FlagConfigurationEntity();
        c.flagId = flagId;
        c.environmentId = environmentId;
        c.enabled = false;
        c.rolloutPercentage = 0;
        c.updatedAt = now;
        c.updatedBy = actor;

        return c;
    }

    /** The stored client-editable state, validated as domain rules. */
    public FlagConfigurationRules rules() {
        return new FlagConfigurationRules(enabled, rolloutPercentage, allowUsers, denyUsers);
    }

    /** Replaces the client-editable state. The JPA {@code @Version} increments when this is flushed. */
    public void apply(FlagConfigurationRules rules, String actor, Instant now) {
        this.enabled = rules.enabled();
        this.rolloutPercentage = (short) rules.rolloutPercentage();
        this.allowUsers = new ArrayList<>(rules.allowUsers());
        this.denyUsers = new ArrayList<>(rules.denyUsers());
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }

    public UUID getFlagId() { return flagId; }

    public UUID getEnvironmentId() { return environmentId; }

    public boolean isEnabled() { return enabled; }

    public short getRolloutPercentage() { return rolloutPercentage; }

    public List<String> getAllowUsers() { return List.copyOf(allowUsers); }

    public List<String> getDenyUsers() { return List.copyOf(denyUsers); }

    public Long getVersion() { return version; }

    public Instant getUpdatedAt() { return updatedAt; }

    public String getUpdatedBy() { return updatedBy; }
}
