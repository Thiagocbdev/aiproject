package com.thiago.aidata.service;

import com.thiago.aidata.dto.TrainingExampleDto;
import com.thiago.aidata.dto.TrainingExampleInput;
import com.thiago.aidata.model.TrainingExampleEntity;
import com.thiago.aidata.repository.TrainingExampleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TrainingService {

    private final TrainingExampleRepository repository;

    public List<TrainingExampleDto> list(int limit, String provider) {
        PageRequest page = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<TrainingExampleEntity> entities;
        if (provider != null && !provider.isBlank()) {
            entities = repository.findByProvider(provider, page);
        } else {
            entities = repository.findAll(page).getContent();
        }
        return entities.stream().map(this::toDto).toList();
    }

    public TrainingExampleDto create(TrainingExampleInput input) {
        TrainingExampleEntity entity = TrainingExampleEntity.builder()
            .provider(input.provider())
            .message(input.message())
            .response(input.response())
            .toolsUsed(input.toolsUsed())
            .rating(input.rating())
            .build();
        return toDto(repository.save(entity));
    }

    private TrainingExampleDto toDto(TrainingExampleEntity e) {
        return new TrainingExampleDto(
            e.getId(),
            e.getProvider(),
            e.getMessage(),
            e.getResponse(),
            e.getToolsUsed(),
            e.getRating(),
            e.getCreatedAt()
        );
    }
}
