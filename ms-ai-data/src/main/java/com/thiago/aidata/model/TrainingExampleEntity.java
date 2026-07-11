package com.thiago.aidata.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "training_examples")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrainingExampleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String response;

    @ElementCollection
    @CollectionTable(
        name = "training_example_tools",
        joinColumns = @JoinColumn(name = "example_id")
    )
    @Column(name = "tool")
    private List<String> toolsUsed;

    private Integer rating;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
