package com.thiago.aidata.controller;

import com.thiago.aidata.dto.TurnDto;
import com.thiago.aidata.dto.TurnInput;
import com.thiago.aidata.dto.TurnResponseInput;
import com.thiago.aidata.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    @PostMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.CREATED)
    public void ensureSession(@PathVariable String sessionId) {
        sessionService.ensureSession(sessionId);
    }

    @PostMapping("/{sessionId}/turns")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> createTurn(
        @PathVariable String sessionId,
        @RequestBody TurnInput input
    ) {
        Long turnId = sessionService.createTurn(sessionId, input);
        return Map.of("turnId", turnId);
    }

    @PostMapping("/{sessionId}/turns/{turnId}/responses")
    @ResponseStatus(HttpStatus.CREATED)
    public void saveTurnResponse(
        @PathVariable String sessionId,
        @PathVariable Long turnId,
        @RequestBody TurnResponseInput input
    ) {
        sessionService.saveTurnResponse(turnId, input);
    }

    @GetMapping("/{sessionId}/turns")
    public List<TurnDto> getTurns(@PathVariable String sessionId) {
        return sessionService.getTurns(sessionId);
    }
}
