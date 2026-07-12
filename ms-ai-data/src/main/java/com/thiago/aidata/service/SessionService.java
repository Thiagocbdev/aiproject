package com.thiago.aidata.service;

import com.thiago.aidata.dto.*;
import com.thiago.aidata.model.ConversationTurnEntity;
import com.thiago.aidata.model.SessionEntity;
import com.thiago.aidata.model.TurnResponseEntity;
import com.thiago.aidata.repository.ConversationTurnRepository;
import com.thiago.aidata.repository.SessionRepository;
import com.thiago.aidata.repository.TurnResponseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessionRepository;
    private final ConversationTurnRepository turnRepository;
    private final TurnResponseRepository responseRepository;

    @Transactional
    public void ensureSession(String sessionId) {
        if (!sessionRepository.existsById(sessionId)) {
            sessionRepository.save(SessionEntity.builder().id(sessionId).build());
        }
    }

    @Transactional
    public Long createTurn(String sessionId, TurnInput input) {
        int nextTurnNumber = input.turnNumber() > 0
            ? input.turnNumber()
            : turnRepository.countBySessionId(sessionId) + 1;

        ConversationTurnEntity turn = ConversationTurnEntity.builder()
            .sessionId(sessionId)
            .question(input.question())
            .useContext(input.useContext())
            .turnNumber(nextTurnNumber)
            .build();
        return turnRepository.save(turn).getId();
    }

    @Transactional
    public void saveTurnResponse(Long turnId, TurnResponseInput input) {
        TurnResponseEntity entity = TurnResponseEntity.builder()
            .turnId(turnId)
            .provider(input.provider())
            .responseText(input.responseText())
            .tokensIn(input.tokensIn())
            .tokensOut(input.tokensOut())
            .cacheHit(input.cacheHit())
            .ragUsed(input.ragUsed())
            .toolsUsed(input.toolsUsed())
            .durationMs(input.durationMs())
            .build();
        responseRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<TurnDto> getTurns(String sessionId) {
        List<ConversationTurnEntity> turns = turnRepository.findBySessionIdOrderByTurnNumber(sessionId);
        if (turns.isEmpty()) return List.of();

        List<Long> turnIds = turns.stream().map(ConversationTurnEntity::getId).toList();
        List<TurnResponseEntity> allResponses = responseRepository.findByTurnIdIn(turnIds);

        Map<Long, List<TurnResponseEntity>> byTurn = allResponses.stream()
            .collect(Collectors.groupingBy(TurnResponseEntity::getTurnId));

        return turns.stream().map(t -> new TurnDto(
            t.getId(),
            t.getSessionId(),
            t.getTurnNumber(),
            t.getQuestion(),
            t.isUseContext(),
            byTurn.getOrDefault(t.getId(), List.of()).stream().map(this::toResponseDto).toList(),
            t.getCreatedAt()
        )).toList();
    }

    private TurnResponseDto toResponseDto(TurnResponseEntity e) {
        return new TurnResponseDto(
            e.getId(), e.getProvider(), e.getResponseText(),
            e.getTokensIn(), e.getTokensOut(), e.isCacheHit(), e.isRagUsed(),
            e.getToolsUsed(), e.getDurationMs()
        );
    }
}
