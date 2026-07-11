package com.thiago.hotelconcierge.model;

public record ProviderStatus(String id, String label, boolean online, boolean primary, String authType) {}
