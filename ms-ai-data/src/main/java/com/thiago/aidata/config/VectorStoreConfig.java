package com.thiago.aidata.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPooled;

@Configuration
@Slf4j
public class VectorStoreConfig {

    @Value("${spring.ai.vectorstore.redis.uri:redis://localhost:6379}")
    private String redisUri;

    @Value("${spring.ai.vectorstore.redis.index:hotel-knowledge}")
    private String indexName;

    @Value("${spring.ai.vectorstore.redis.prefix:hotel:doc:}")
    private String prefix;

    @Bean
    public JedisPooled jedisPooled() {
        log.info("Connecting JedisPooled to: {}", redisUri);
        return new JedisPooled(redisUri);
    }

    @Bean
    public VectorStore vectorStore(JedisPooled jedisPooled, EmbeddingModel embeddingModel) {
        log.info("Building RedisVectorStore index='{}' prefix='{}'", indexName, prefix);
        return RedisVectorStore.builder(jedisPooled, embeddingModel)
                .indexName(indexName)
                .prefix(prefix)
                .initializeSchema(true)
                .build();
    }
}
