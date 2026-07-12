package com.thiago.hotelconcierge.tools;

import com.thiago.hotelconcierge.client.HotelInfoClient;
import com.thiago.hotelconcierge.service.SseSessionStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class HotelTools {
    private final HotelInfoClient hotelInfoClient;
    private final SseSessionStore sessionStore;

    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> PROVIDER = new ThreadLocal<>();
    private static final ThreadLocal<List<String>> TOOLS_USED = ThreadLocal.withInitial(ArrayList::new);

    public static void setContext(String requestId, String provider) {
        REQUEST_ID.set(requestId);
        PROVIDER.set(provider);
        TOOLS_USED.get().clear();
    }

    public static void clearContext() {
        REQUEST_ID.remove();
        PROVIDER.remove();
        TOOLS_USED.remove();
    }

    public static List<String> getAndClearToolsUsed() {
        List<String> used = new ArrayList<>(TOOLS_USED.get());
        TOOLS_USED.get().clear();
        return used;
    }

    @Tool(description = "Verifica disponibilidade de um serviço do hotel (spa, restaurant, gym, room_service) em uma data e horário específicos. Retorna os horários disponíveis.")
    public String checkAvailability(String serviceType, String date, String time) {
        String requestId = REQUEST_ID.get();
        String provider = PROVIDER.get();
        TOOLS_USED.get().add("check_availability");

        Map<String, Object> toolCallData = Map.of(
            "provider", provider != null ? provider : "unknown",
            "tool", "check_availability",
            "args", Map.of("serviceType", serviceType, "date", date, "time", time != null ? time : "")
        );
        if (requestId != null) sessionStore.emit(requestId, "tool_call", toolCallData);

        try {
            Map<String, Object> result = hotelInfoClient.getAvailability(serviceType, date, time);
            Map<String, Object> resultData = Map.of(
                "provider", provider != null ? provider : "unknown",
                "tool", "check_availability",
                "result", result
            );
            if (requestId != null) sessionStore.emit(requestId, "tool_result", resultData);
            return result.toString();
        } catch (Exception e) {
            log.warn("check_availability failed: {}", e.getMessage());
            return "Não foi possível verificar disponibilidade agora. Tente novamente.";
        }
    }

    @Tool(description = "Consulta o preço de um serviço ou tipo de quarto do hotel. Informe o tipo (spa, restaurant, standard, deluxe, suite) e a data desejada.")
    public String getPrice(String serviceType, String date) {
        String requestId = REQUEST_ID.get();
        String provider = PROVIDER.get();
        TOOLS_USED.get().add("get_price");

        Map<String, Object> toolCallData = Map.of(
            "provider", provider != null ? provider : "unknown",
            "tool", "get_price",
            "args", Map.of("serviceType", serviceType, "date", date)
        );
        if (requestId != null) sessionStore.emit(requestId, "tool_call", toolCallData);

        try {
            if (date == null || date.isBlank()) date = LocalDate.now().toString();
            Map<String, Object> result = hotelInfoClient.getPrice(serviceType, date);
            Map<String, Object> resultData = Map.of(
                "provider", provider != null ? provider : "unknown",
                "tool", "get_price",
                "result", result
            );
            if (requestId != null) sessionStore.emit(requestId, "tool_result", resultData);
            return result.toString();
        } catch (Exception e) {
            log.warn("get_price failed: {}", e.getMessage());
            return "Não foi possível consultar o preço agora.";
        }
    }

    @Tool(description = "Prepara uma reserva de serviço. IMPORTANTE: Retorna 'pending_guest_confirmation' - aguarde o hóspede confirmar antes de concluir a reserva. Parâmetros: guestId, serviceType (spa/restaurant/gym), date (YYYY-MM-DD), time (HH:mm).")
    public String createBooking(String guestId, String serviceType, String date, String time) {
        String requestId = REQUEST_ID.get();
        String provider = PROVIDER.get();
        TOOLS_USED.get().add("create_booking");

        String pendingActionId = UUID.randomUUID().toString();
        Map<String, Object> bookingArgs = Map.of(
            "guestId", guestId != null ? guestId : "11111111-1111-1111-1111-111111111111",
            "serviceType", serviceType,
            "date", date,
            "time", time
        );

        sessionStore.storePendingAction(pendingActionId, bookingArgs);

        Map<String, Object> toolCallData = Map.of(
            "provider", provider != null ? provider : "unknown",
            "tool", "create_booking",
            "args", bookingArgs,
            "pendingActionId", pendingActionId
        );
        if (requestId != null) sessionStore.emit(requestId, "tool_call", toolCallData);

        return "pending_guest_confirmation|pendingActionId=" + pendingActionId;
    }

    @Tool(description = "Obtém o perfil completo do hóspede pelo ID. Use para personalizar respostas com informações de fidelidade, preferências e histórico.")
    public String getGuestProfile(String guestId) {
        String requestId = REQUEST_ID.get();
        String provider = PROVIDER.get();
        TOOLS_USED.get().add("get_guest_profile");

        Map<String, Object> toolCallData = Map.of(
            "provider", provider != null ? provider : "unknown",
            "tool", "get_guest_profile",
            "args", Map.of("guestId", guestId)
        );
        if (requestId != null) sessionStore.emit(requestId, "tool_call", toolCallData);

        try {
            Map<String, Object> result = hotelInfoClient.getGuest(guestId);
            Map<String, Object> resultData = Map.of(
                "provider", provider != null ? provider : "unknown",
                "tool", "get_guest_profile",
                "result", result
            );
            if (requestId != null) sessionStore.emit(requestId, "tool_result", resultData);
            return result.toString();
        } catch (Exception e) {
            log.warn("get_guest_profile failed: {}", e.getMessage());
            return "Hóspede não encontrado ou ID inválido.";
        }
    }

    @Tool(description = "Busca atrações locais, pontos turísticos, restaurantes e atividades próximas ao hotel. Use para sugestões de passeios e recomendações.")
    public String searchLocalAttractions(String query) {
        String requestId = REQUEST_ID.get();
        String provider = PROVIDER.get();
        TOOLS_USED.get().add("search_local_attractions");

        Map<String, Object> toolCallData = Map.of(
            "provider", provider != null ? provider : "unknown",
            "tool", "search_local_attractions",
            "args", Map.of("query", query)
        );
        if (requestId != null) sessionStore.emit(requestId, "tool_call", toolCallData);

        // Static attractions data
        String attractions = """
            Atrações locais próximas ao hotel:
            - Parque Municipal: 800m, gratuito, abre 6h-20h, ótimo para caminhada
            - Shopping Central: 1.2km, cinema + lojas + praça de alimentação
            - Catedral Histórica: 2km, visitas guiadas 10h e 15h (R$15)
            - Praia da Lagoa: 15km, 20min de carro, acesso gratuito
            - Museu de Arte: 3km, entrada R$20 (grátis às terças)
            - Mercado Municipal: 1km, produtos regionais e artesanato
            - Passeio de barco: saídas às 9h e 15h (R$80/pessoa)
            - Trilha Ecológica: 8km, guia incluso R$120
            - Cantina da Vovó: restaurante típico recomendado, 2.5km
            """;

        Map<String, Object> resultData = Map.of(
            "provider", provider != null ? provider : "unknown",
            "tool", "search_local_attractions",
            "result", Map.of("query", query, "results", attractions)
        );
        if (requestId != null) sessionStore.emit(requestId, "tool_result", resultData);

        return attractions;
    }
}
