package com.thiago.aidata.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "turn_responses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TurnResponseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "turn_id", nullable = false)
    private Long turnId;

    @Column(nullable = false)
    private String provider;

    @Column(name = "response_text", columnDefinition = "TEXT")
    private String responseText;

    @Column(name = "tokens_in", nullable = false)
    private int tokensIn;

    @Column(name = "tokens_out", nullable = false)
    private int tokensOut;

    @Column(name = "cache_hit", nullable = false)
    private boolean cacheHit;

    @Column(name = "rag_used", nullable = false)
    private boolean ragUsed;

    @Column(name = "duration_ms")
    private Long durationMs;

    @ElementCollection
    @CollectionTable(
        name = "turn_response_tools",
        joinColumns = @JoinColumn(name = "response_id")
    )
    @Column(name = "tool")
    private List<String> toolsUsed;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
