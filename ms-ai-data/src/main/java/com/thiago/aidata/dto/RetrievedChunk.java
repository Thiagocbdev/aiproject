package com.thiago.aidata.dto;

import java.util.Map;

public record RetrievedChunk(
    String id,
    String content,
    float score,
    Map<String, Object> metadata
) {}
