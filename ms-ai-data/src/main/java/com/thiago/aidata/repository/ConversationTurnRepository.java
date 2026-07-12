package com.thiago.aidata.repository;

import com.thiago.aidata.model.ConversationTurnEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversationTurnRepository extends JpaRepository<ConversationTurnEntity, Long> {
    List<ConversationTurnEntity> findBySessionIdOrderByTurnNumber(String sessionId);
    int countBySessionId(String sessionId);
}
