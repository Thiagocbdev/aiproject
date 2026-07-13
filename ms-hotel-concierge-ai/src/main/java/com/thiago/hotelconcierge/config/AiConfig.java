package com.thiago.hotelconcierge.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
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

    @Value("${concierge.openrouter.model-primary:openai/gpt-oss-120b:free}")
    private String modelPrimary;

    @Value("${concierge.gemini.model:gemini-3.1-flash-lite}")
    private String geminiModel;

    @Value("${GEMINI_API_KEY:missing}")
    private String geminiApiKey;

    // Slot "booking" — gpt-oss-120b via OpenRouter (temp baixa, precisão)
    // Bean mantém nome "anthropicChatClient" para não quebrar ProviderOrchestrator
    @Bean("anthropicChatClient")
    public ChatClient openRouterBookingChatClient(OpenAiChatModel model) {
        return ChatClient.builder(model)
            .defaultSystem(buildSystemPrompt("hotel concierge especializado em reservas e informações precisas"))
            .defaultOptions(OpenAiChatOptions.builder()
                .model(modelPrimary)
                .temperature(bookingTemperature)
                .build())
            .build();
    }

    // Slot "FAQ/RAG" — Gemini 2.0 Flash via Google AI Studio (endpoint OpenAI-compatível)
    // Bean mantém nome "openAiChatClient" para não quebrar ProviderOrchestrator
    @Bean("openAiChatClient")
    public ChatClient geminiFaqChatClient() {
        OpenAiApi geminiApi = OpenAiApi.builder()
            .baseUrl("https://generativelanguage.googleapis.com")
            .apiKey(geminiApiKey)
            .completionsPath("/v1beta/openai/chat/completions")
            .build();

        OpenAiChatModel geminiChatModel = OpenAiChatModel.builder()
            .openAiApi(geminiApi)
            .defaultOptions(OpenAiChatOptions.builder().model(geminiModel).build())
            .build();

        return ChatClient.builder(geminiChatModel)
            .defaultSystem(buildSystemPrompt("hotel concierge especializado em responder perguntas com base em documentos e FAQs"))
            .defaultOptions(OpenAiChatOptions.builder()
                .model(geminiModel)
                .temperature(faqTemperature)
                .build())
            .build();
    }

    // Slot "recomendação" — Ollama local (temp alta, criatividade)
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

            ### Hóspedes
            - search_guests: busca hóspedes pelo nome — use ANTES de pedir ID ao hóspede
            - get_guest_profile: obtém perfil completo pelo ID — verificar ANTES de qualquer reserva
            - create_guest: cadastra novo hóspede (nome e e-mail obrigatórios, telefone opcional)

            ### Quartos
            - list_rooms: lista quartos disponíveis com tipos e preços (filtro opcional por tipo)

            ### Disponibilidade e Preços
            - check_availability: verifica disponibilidade de serviços (spa, restaurant, gym, room_service)
            - get_price: consulta preços de serviços e quartos — NUNCA invente valores

            ### Reservas de Serviço
            - create_booking: prepara reserva de serviço — AGUARDA confirmação explícita do hóspede
            - get_guest_bookings: lista todas as reservas de um hóspede
            - get_booking: detalhes de uma reserva específica pelo ID
            - cancel_booking: cancela uma reserva pelo ID

            ### Lazer
            - search_local_attractions: busca pontos turísticos e atrações locais

            ## Regras de negócio obrigatórias

            ### Cadastro
            - Toda reserva exige hóspede cadastrado. Se não encontrado, use search_guests pelo nome.
            - Se não cadastrado, colete nome (obrigatório) e e-mail (obrigatório pelo sistema)
              e ofereça o cadastro ANTES de prosseguir com a reserva.

            ### Reservas
            - SEMPRE buscar o hóspede via search_guests ou get_guest_profile antes de criar reserva.
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
