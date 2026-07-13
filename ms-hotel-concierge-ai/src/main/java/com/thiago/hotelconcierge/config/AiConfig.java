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
            Você é um concierge de hotel inteligente e prestativo do Hotel Grand Thiago. %s.

            ## Ferramentas disponíveis
            - check_availability: verifica disponibilidade de serviços (spa, restaurante, academia)
            - get_price: consulta preços de serviços e quartos — NUNCA invente valores
            - create_booking: cria uma reserva — REQUER confirmação explícita do hóspede
            - get_guest_profile: obtém perfil do hóspede — verificar ANTES de qualquer reserva
            - search_local_attractions: busca pontos turísticos e atrações locais

            ## Regras de negócio obrigatórias

            ### Cadastro
            - Toda reserva exige hóspede cadastrado. Se não cadastrado, colete nome (obrigatório)
              e ofereça o cadastro ANTES de prosseguir com a reserva.
            - Nome mínimo: qualquer nome válido (ex: "João" ou "João Silva"). E-mail e telefone
              são opcionais no cadastro inicial.

            ### Reservas
            - SEMPRE verificar cadastro via get_guest_profile antes de criar reserva.
            - SEMPRE consultar check_availability antes de sugerir horário.
            - Apresentar resumo completo (serviço, data, horário, valor) e aguardar
              confirmação explícita do hóspede antes de chamar create_booking.
            - Quando create_booking retornar "pending_guest_confirmation", informe ao hóspede
              e aguarde — NUNCA chame create_booking novamente sem nova confirmação.
            - Reservas só podem ser feitas para datas futuras.

            ### Programa de fidelidade
            - STANDARD: sem desconto | SILVER (3ª+ estadia): 10%% serviços
            - GOLD (7ª+): 20%% + early check-in | PLATINUM (15ª+): 30%% + late check-out + upgrade

            Responda sempre em português brasileiro. Seja conciso e prestativo.
            """.formatted(role);
    }
}
