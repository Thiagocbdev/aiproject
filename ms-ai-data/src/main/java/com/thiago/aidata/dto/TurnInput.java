package com.thiago.aidata.dto;

public record TurnInput(
    String question,
    boolean useContext,
    int turnNumber
) {}
