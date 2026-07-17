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

    static final int TOP_RATED_MIN_RATING = 4;
    static final int TOP_RATED_DEFAULT_LIMIT = 2;
    static final int TOP_RATED_MAX_LIMIT = 10;

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

    public List<TrainingExampleDto> topRated(String provider, int limit) {
        int effectiveLimit = limit < 1 ? TOP_RATED_DEFAULT_LIMIT : Math.min(limit, TOP_RATED_MAX_LIMIT);
        PageRequest page = PageRequest.of(0, effectiveLimit, Sort.by(
            Sort.Order.desc("rating"),
            Sort.Order.desc("createdAt")
        ));
        return repository.findByProviderAndRatingGreaterThanEqual(provider, TOP_RATED_MIN_RATING, page)
            .stream().map(this::toDto).toList();
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
