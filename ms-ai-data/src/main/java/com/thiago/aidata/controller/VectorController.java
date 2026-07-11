package com.thiago.aidata.controller;

import com.thiago.aidata.dto.RetrievedChunk;
import com.thiago.aidata.dto.VectorIndexRequest;
import com.thiago.aidata.dto.VectorSearchRequest;
import com.thiago.aidata.service.VectorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/vectors")
@RequiredArgsConstructor
public class VectorController {

    private final VectorService vectorService;

    @PostMapping("/index")
    @ResponseStatus(HttpStatus.CREATED)
    public void index(@Valid @RequestBody VectorIndexRequest request) {
        vectorService.indexFromRequest(request);
    }

    @PostMapping("/search")
    public List<RetrievedChunk> search(@Valid @RequestBody VectorSearchRequest request) {
        return vectorService.search(request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        vectorService.delete(id);
    }
}
