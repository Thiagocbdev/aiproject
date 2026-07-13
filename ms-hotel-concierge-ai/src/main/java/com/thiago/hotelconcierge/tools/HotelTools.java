package com.thiago.hotelconcierge.tools;

import com.thiago.hotelconcierge.client.HotelInfoClient;
import com.thiago.hotelconcierge.service.SseSessionStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
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

    public static void setContext(String requestId, String provider) {
        REQUEST_ID.set(requestId);
        PROVIDER.set(provider);
    }

    public static void clearContext() {
        REQUEST_ID.remove();
        PROVIDER.remove();
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private void emitToolCall(String tool, Map<String, Object> args) {
        String requestId = REQUEST_ID.get();
        String provider = PROVIDER.get();
        if (requestId != null) {
            sessionStore.emit(requestId, "tool_call", Map.of(
                "provider", provider != null ? provider : "unknown",
                "tool", tool,
                "args", args
            ));
        }
    }

    private void emitToolResult(String tool, Object result) {
        String requestId = REQUEST_ID.get();
        String provider = PROVIDER.get();
        if (requestId != null) {
            sessionStore.emit(requestId, "tool_result", Map.of(
                "provider", provider != null ? provider : "unknown",
                "tool", tool,
                "result", result
            ));
        }
    }

    // ── Guests ───────────────────────────────────────────────────────

    @Tool(description = "Busca hóspedes cadastrados pelo nome. Use para encontrar o ID do hóspede antes de criar reservas ou consultar perfil.")
    public String searchGuests(String name) {
        emitToolCall("search_guests", Map.of("name", name != null ? name : ""));
        try {
            List<Map<String, Object>> result = hotelInfoClient.searchGuests(name);
            emitToolResult("search_guests", result);
            if (result == null || result.isEmpty()) return "Nenhum hóspede encontrado com o nome: " + name;
            return result.toString();
        } catch (Exception e) {
            log.warn("search_guests failed: {}", e.getMessage());
            return "Não foi possível buscar hóspedes agora.";
        }
    }

    @Tool(description = "Obtém o perfil completo do hóspede pelo ID UUID. Use o campo 'id' (UUID) retornado por searchGuests — NUNCA passe nomes ou textos livres como ID.")
    public String getGuestProfile(String guestId) {
        emitToolCall("get_guest_profile", Map.of("guestId", guestId));
        try {
            Map<String, Object> result = hotelInfoClient.getGuest(guestId);
            emitToolResult("get_guest_profile", result);
            return result.toString();
        } catch (Exception e) {
            log.warn("get_guest_profile failed: {}", e.getMessage());
            return "Hóspede não encontrado ou ID inválido.";
        }
    }

    @Tool(description = "Cadastra um novo hóspede no sistema. Nome e e-mail são obrigatórios. Telefone é opcional. Retorna o perfil criado com o ID do hóspede.")
    public String createGuest(String name, String email, String phone) {
        emitToolCall("create_guest", Map.of("name", name, "email", email != null ? email : "", "phone", phone != null ? phone : ""));
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("name", name);
            body.put("email", email);
            if (phone != null && !phone.isBlank()) body.put("phone", phone);
            Map<String, Object> result = hotelInfoClient.createGuest(body);
            emitToolResult("create_guest", result);
            return "Hóspede cadastrado com sucesso: " + result.toString();
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("409") || msg.contains("Conflict") || msg.contains("e-mail")) {
                return "Já existe um hóspede cadastrado com este e-mail. Use searchGuests para encontrá-lo.";
            }
            log.warn("create_guest failed: {}", msg);
            return "Não foi possível cadastrar o hóspede. Verifique nome e e-mail e tente novamente.";
        }
    }

    // ── Rooms ─────────────────────────────────────────────────────────

    @Tool(description = "Lista os quartos disponíveis no hotel. Filtro opcional por tipo: STANDARD, DELUXE ou SUITE. Retorna número, andar, tipo, capacidade e preço por noite.")
    public String listRooms(String type) {
        emitToolCall("list_rooms", Map.of("type", type != null ? type : "todos"));
        try {
            List<Map<String, Object>> result = hotelInfoClient.listRooms(type);
            emitToolResult("list_rooms", result);
            if (result == null || result.isEmpty()) return "Nenhum quarto encontrado" + (type != null ? " do tipo " + type : "") + ".";
            return result.toString();
        } catch (Exception e) {
            log.warn("list_rooms failed: {}", e.getMessage());
            return "Não foi possível listar os quartos agora.";
        }
    }

    // ── Availability & Pricing ────────────────────────────────────────

    @Tool(description = "Verifica disponibilidade de um serviço do hotel (spa, restaurant, gym, room_service) em uma data. serviceType deve ser um desses 4 valores exatos. date DEVE estar no formato ISO YYYY-MM-DD (ex: 2026-07-15) — NUNCA use palavras em português como 'amanhã'.")
    public String checkAvailability(String serviceType, String date, String time) {
        emitToolCall("check_availability", Map.of("serviceType", serviceType, "date", date, "time", time != null ? time : ""));
        try {
            Map<String, Object> result = hotelInfoClient.getAvailability(serviceType, date, time);
            emitToolResult("check_availability", result);
            return result.toString();
        } catch (Exception e) {
            log.warn("check_availability failed: {}", e.getMessage());
            return "Não foi possível verificar disponibilidade agora. Tente novamente.";
        }
    }

    @Tool(description = "Consulta o preço de um serviço do hotel. serviceType deve ser: spa, restaurant, gym ou room_service. date DEVE estar no formato ISO YYYY-MM-DD (ex: 2026-07-15) — NUNCA use palavras em português como 'amanhã'.")
    public String getPrice(String serviceType, String date) {
        emitToolCall("get_price", Map.of("serviceType", serviceType, "date", date));
        try {
            if (date == null || date.isBlank()) date = LocalDate.now().toString();
            Map<String, Object> result = hotelInfoClient.getPrice(serviceType, date);
            emitToolResult("get_price", result);
            return result.toString();
        } catch (Exception e) {
            log.warn("get_price failed: {}", e.getMessage());
            return "Não foi possível consultar o preço agora.";
        }
    }

    // ── Service Bookings ─────────────────────────────────────────────

    @Tool(description = "Prepara uma reserva de serviço (spa, restaurant, gym). IMPORTANTE: Retorna 'pending_guest_confirmation' — aguarde o hóspede confirmar antes de concluir. Parâmetros: guestId, serviceType, date (YYYY-MM-DD), time (HH:mm).")
    public String createBooking(String guestId, String serviceType, String date, String time) {
        String pendingActionId = UUID.randomUUID().toString();
        Map<String, Object> bookingArgs = Map.of(
            "guestId", guestId != null ? guestId : "",
            "serviceType", serviceType,
            "date", date,
            "time", time
        );
        sessionStore.storePendingAction(pendingActionId, bookingArgs);
        emitToolCall("create_booking", Map.of(
            "args", bookingArgs,
            "pendingActionId", pendingActionId
        ));
        return "pending_guest_confirmation|pendingActionId=" + pendingActionId;
    }

    @Tool(description = "Lista todas as reservas de um hóspede pelo seu ID. Retorna histórico de reservas com status, data, serviço e horário.")
    public String getGuestBookings(String guestId) {
        emitToolCall("get_guest_bookings", Map.of("guestId", guestId));
        try {
            List<Map<String, Object>> result = hotelInfoClient.listBookings(guestId);
            emitToolResult("get_guest_bookings", result);
            if (result == null || result.isEmpty()) return "Nenhuma reserva encontrada para o hóspede.";
            return result.toString();
        } catch (Exception e) {
            log.warn("get_guest_bookings failed: {}", e.getMessage());
            return "Não foi possível buscar as reservas agora.";
        }
    }

    @Tool(description = "Obtém os detalhes completos de uma reserva específica pelo seu ID.")
    public String getBooking(String bookingId) {
        emitToolCall("get_booking", Map.of("bookingId", bookingId));
        try {
            Map<String, Object> result = hotelInfoClient.getBooking(bookingId);
            emitToolResult("get_booking", result);
            return result.toString();
        } catch (Exception e) {
            log.warn("get_booking failed: {}", e.getMessage());
            return "Reserva não encontrada ou ID inválido.";
        }
    }

    @Tool(description = "Cancela uma reserva pelo seu ID. Política: cancelamento com menos de 48h de antecedência gera multa de 100% do valor do serviço.")
    public String cancelBooking(String bookingId) {
        emitToolCall("cancel_booking", Map.of("bookingId", bookingId));
        try {
            Map<String, Object> result = hotelInfoClient.updateBooking(bookingId, Map.of("status", "CANCELLED"));
            emitToolResult("cancel_booking", result);
            return "Reserva cancelada: " + result.toString();
        } catch (Exception e) {
            log.warn("cancel_booking failed: {}", e.getMessage());
            return "Não foi possível cancelar a reserva. Verifique o ID e tente novamente.";
        }
    }

    // ── Attractions ──────────────────────────────────────────────────

    @Tool(description = "Busca atrações locais, pontos turísticos, restaurantes e atividades próximas ao hotel. Use para sugestões de passeios e recomendações.")
    public String searchLocalAttractions(String query) {
        emitToolCall("search_local_attractions", Map.of("query", query));
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
        emitToolResult("search_local_attractions", Map.of("query", query, "results", attractions));
        return attractions;
    }
}
