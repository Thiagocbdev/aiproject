package com.thiago.hotelconcierge.controller;

import com.thiago.hotelconcierge.model.ProviderStatus;
import com.thiago.hotelconcierge.model.TemperatureProfiles;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/providers")
@RequiredArgsConstructor
public class ProviderController {
    @Value("${concierge.temperature.booking:0.15}") private double booking;
    @Value("${concierge.temperature.faq:0.35}") private double faq;
    @Value("${concierge.temperature.recommendation:0.80}") private double recommendation;
    @Value("${spring.ai.anthropic.api-key:}") private String anthropicKey;
    @Value("${spring.ai.openai.api-key:}") private String openaiKey;

    @GetMapping
    public List<ProviderStatus> listProviders() {
        boolean anthropicOnline = anthropicKey != null && !anthropicKey.isBlank() && !anthropicKey.equals("sk-ant-placeholder");
        boolean openaiOnline = openaiKey != null && !openaiKey.isBlank() && !openaiKey.equals("sk-placeholder");
        return List.of(
            new ProviderStatus("anthropic", "Anthropic", anthropicOnline, true, "api-key"),
            new ProviderStatus("openai", "OpenAI", openaiOnline, false, "api-key"),
            new ProviderStatus("ollama", "Ollama (local)", true, false, "none")
        );
    }

    @GetMapping("/temperature-profiles")
    public TemperatureProfiles getTemperatureProfiles() {
        return new TemperatureProfiles(booking, faq, recommendation);
    }
}
