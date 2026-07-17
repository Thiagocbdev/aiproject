package com.thiago.aidata.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
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

    @Value("${cache.semantic-index:semantic-cache}")
    private String semanticCacheIndexName;

    @Value("${cache.semantic-prefix:cache:sem:}")
    private String semanticCachePrefix;

    @Bean
    public JedisPooled jedisPooled() {
        log.info("Connecting JedisPooled to: {}", redisUri);
        return new JedisPooled(redisUri);
    }

    @Bean
    @Primary
    public VectorStore vectorStore(JedisPooled jedisPooled, EmbeddingModel embeddingModel) {
        log.info("Building RedisVectorStore index='{}' prefix='{}'", indexName, prefix);
        // IMPORTANTE: RedisVectorStore só filtra/retorna metadados declarados como
        // MetadataField. "category" (tag) habilita o filtro `category IN (...)` do
        // VectorService; "source" (text) preserva a origem do chunk na resposta.
        // ATENÇÃO: initializeSchema NÃO recria índice pré-existente — em ambiente
        // com índice antigo é preciso FT.DROPINDEX hotel-knowledge (sem DD) + reindex.
        return RedisVectorStore.builder(jedisPooled, embeddingModel)
                .indexName(indexName)
                .prefix(prefix)
                .metadataFields(
                        RedisVectorStore.MetadataField.tag("category"),
                        RedisVectorStore.MetadataField.text("source"))
                .initializeSchema(true)
                .build();
    }

    @Bean
    public VectorStore semanticCacheVectorStore(JedisPooled jedisPooled, EmbeddingModel embeddingModel) {
        log.info("Building semantic cache RedisVectorStore index='{}' prefix='{}'",
                semanticCacheIndexName, semanticCachePrefix);
        // IMPORTANTE: RedisVectorStore só devolve na busca os metadados declarados
        // como MetadataField (toDocument itera apenas sobre metadataFields).
        // "response", "createdAt" e "ttlSeconds" precisam estar declarados aqui,
        // senão o lookup semântico voltaria sem resposta e sem controle de TTL.
        return RedisVectorStore.builder(jedisPooled, embeddingModel)
                .indexName(semanticCacheIndexName)
                .prefix(semanticCachePrefix)
                .metadataFields(
                        RedisVectorStore.MetadataField.tag("provider"),
                        RedisVectorStore.MetadataField.text("response"),
                        RedisVectorStore.MetadataField.numeric("createdAt"),
                        RedisVectorStore.MetadataField.numeric("ttlSeconds"))
                .initializeSchema(true)
                .build();
    }
}
