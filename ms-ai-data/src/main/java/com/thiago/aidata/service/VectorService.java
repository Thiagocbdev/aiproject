package com.thiago.aidata.service;

import com.thiago.aidata.dto.RetrievedChunk;
import com.thiago.aidata.dto.VectorIndexRequest;
import com.thiago.aidata.dto.VectorSearchRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class VectorService {

    private final VectorStore vectorStore;

    public void index(String id, String content, Map<String, String> metadata) {
        Document doc = new Document(id, content, Map.copyOf(metadata));
        vectorStore.add(List.of(doc));
    }

    public void indexFromRequest(VectorIndexRequest request) {
        Map<String, Object> meta = request.metadata() != null
            ? Map.copyOf(request.metadata())
            : Map.of();
        Document doc = new Document(request.id(), request.content(), meta);
        vectorStore.add(List.of(doc));
    }

    public List<RetrievedChunk> search(VectorSearchRequest request) {
        int topK = request.topK() != null ? request.topK() : 3;
        SearchRequest searchRequest = SearchRequest.builder()
            .query(request.query())
            .topK(topK)
            .build();
        try {
            return vectorStore.similaritySearch(searchRequest).stream()
                .map(doc -> new RetrievedChunk(
                    doc.getId(),
                    doc.getText(),
                    doc.getScore() != null ? doc.getScore().floatValue() : 0f,
                    doc.getMetadata()
                ))
                .toList();
        } catch (Exception e) {
            log.warn("Vector search unavailable (index may not exist): {}", e.getMessage());
            return List.of();
        }
    }

    public void delete(String id) {
        vectorStore.delete(List.of(id));
    }
}
