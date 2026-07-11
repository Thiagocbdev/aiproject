package com.thiago.aidata.config;

import com.thiago.aidata.service.VectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class RagInitializer {

    private final VectorService vectorService;
    private final ResourceLoader resourceLoader;

    @Value("${rag.re-index-on-startup:false}")
    private boolean reIndexOnStartup;

    @Bean
    ApplicationRunner indexRagDocuments() {
        return args -> {
            if (!reIndexOnStartup) {
                log.info("RAG re-indexing skipped (rag.re-index-on-startup=false)");
                return;
            }
            ResourcePatternResolver resolver = ResourcePatternUtils.getResourcePatternResolver(resourceLoader);
            Resource[] resources = resolver.getResources("classpath:rag/*.md");
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                try {
                    String content = resource.getContentAsString(StandardCharsets.UTF_8);
                    String category = filename != null ? filename.replace(".md", "") : "general";
                    String id = "rag-" + category;

                    String[] chunks = content.split("\n\n+");
                    int indexed = 0;
                    for (int i = 0; i < chunks.length; i++) {
                        String chunk = chunks[i].trim();
                        if (chunk.length() < 50) continue;
                        vectorService.index(id + "-" + i, chunk, Map.of(
                            "source", filename != null ? filename : "",
                            "category", category
                        ));
                        indexed++;
                    }
                    log.info("Indexed RAG document: {} ({} chunks)", filename, indexed);
                } catch (Exception e) {
                    log.warn("Failed to index RAG document {}: {}", filename, e.getMessage());
                }
            }
        };
    }
}
