package com.thiago.aidata.controller;

import com.thiago.aidata.dto.TrainingExampleDto;
import com.thiago.aidata.dto.TrainingExampleInput;
import com.thiago.aidata.service.TrainingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/training")
@RequiredArgsConstructor
public class TrainingController {

    private final TrainingService trainingService;

    @GetMapping("/examples")
    public List<TrainingExampleDto> list(
        @RequestParam(defaultValue = "50") int limit,
        @RequestParam(required = false) String provider
    ) {
        return trainingService.list(limit, provider);
    }

    @GetMapping("/examples/top")
    public List<TrainingExampleDto> topRated(
        @RequestParam String provider,
        @RequestParam(defaultValue = "2") int limit
    ) {
        return trainingService.topRated(provider, limit);
    }

    @PostMapping("/examples")
    @ResponseStatus(HttpStatus.CREATED)
    public TrainingExampleDto create(@Valid @RequestBody TrainingExampleInput input) {
        return trainingService.create(input);
    }
}
