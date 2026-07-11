package com.thiago.aidata.repository;

import com.thiago.aidata.model.TrainingExampleEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TrainingExampleRepository extends JpaRepository<TrainingExampleEntity, UUID> {
    List<TrainingExampleEntity> findByProvider(String provider, Pageable pageable);
}
