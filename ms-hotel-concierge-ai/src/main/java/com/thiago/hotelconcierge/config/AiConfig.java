package com.thiago.hotelconcierge.config;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Value("${concierge.temperature.booking:0.15}")
    private double bookingTemperature;

    @Value("${concierge.temperature.faq:0.35}")
    private double faqTemperature;

    @Value("${concierge.temperature.recommendation:0.80}")
    private double recommendationTemperature;

    @Bean("anthropicChatClient")
    public ChatClient anthropicChatClient(AnthropicChatModel model) {
        return ChatClient.builder(model)
            .defaultSystem(buildSystemPrompt("hotel concierge especializado em reservas e informações precisas"))
            .build();
    }

    @Bean("openAiChatClient")
    public ChatClient openAiChatClient(OpenAiChatModel model) {
        return ChatClient.builder(model)
            .defaultSystem(buildSystemPrompt("hotel concierge especializado em responder perguntas com base em documentos e FAQs"))
            .build();
    }

    @Bean("ollamaChatClient")
    public ChatClient ollamaChatClient(OllamaChatModel model) {
        return ChatClient.builder(model)
            .defaultSystem(buildSystemPrompt("hotel concierge criativo especializado em sugestões e recomendações personalizadas"))
            .build();
    }

    private String buildSystemPrompt(String role) {
        return """
            Você é um concierge de hotel inteligente e prestativo. %s.

            Você tem acesso às seguintes ferramentas:
            - check_availability: verifica disponibilidade de serviços (spa, restaurante, academia)
            - get_price: consulta preços de serviços e quartos
            - create_booking: cria uma reserva (REQUER confirmação explícita do hóspede)
            - get_guest_profile: obtém informações do perfil do hóspede
            - search_local_attractions: busca pontos turísticos e atrações locais

            REGRA IMPORTANTE: Quando chamar create_booking e receber resposta "pending_guest_confirmation",
            informe ao hóspede que a reserva está preparada e aguarde a confirmação dele.
            NUNCA chame create_booking novamente enquanto aguarda confirmação.

            Responda sempre em português brasileiro. Seja conciso e prestativo.
            """.formatted(role);
    }
}
