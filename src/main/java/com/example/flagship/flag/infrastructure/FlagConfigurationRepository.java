package com.example.flagship.flag.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlagConfigurationRepository extends JpaRepository<FlagConfigurationEntity, UUID> {

    List<FlagConfigurationEntity> findByFlagId(UUID flagId);
}
