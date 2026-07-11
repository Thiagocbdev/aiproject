-- Service Types
INSERT INTO service_types (id, name, description, open_time, close_time, slot_duration_minutes, price_per_slot)
VALUES
  (gen_random_uuid(), 'spa', 'Spa com massagens e tratamentos relaxantes', '09:00', '21:00', 60, 150.00),
  (gen_random_uuid(), 'restaurant', 'Restaurante com culinária internacional', '12:00', '23:00', 90, 80.00),
  (gen_random_uuid(), 'gym', 'Academia equipada aberta ao público', '06:00', '22:00', NULL, 0.00),
  (gen_random_uuid(), 'room_service', 'Serviço de quarto disponível 24h', NULL, NULL, NULL, 0.00);

-- Rooms (20 rooms: mix STANDARD/DELUXE/SUITE)
INSERT INTO rooms (id, number, floor, type, capacity, price_per_night, status) VALUES
  (gen_random_uuid(), '101', 1, 'STANDARD', 2, 250.00, 'AVAILABLE'),
  (gen_random_uuid(), '102', 1, 'STANDARD', 2, 250.00, 'AVAILABLE'),
  (gen_random_uuid(), '103', 1, 'STANDARD', 2, 250.00, 'AVAILABLE'),
  (gen_random_uuid(), '104', 1, 'STANDARD', 2, 250.00, 'AVAILABLE'),
  (gen_random_uuid(), '201', 2, 'STANDARD', 2, 250.00, 'AVAILABLE'),
  (gen_random_uuid(), '202', 2, 'DELUXE', 3, 380.00, 'AVAILABLE'),
  (gen_random_uuid(), '203', 2, 'DELUXE', 3, 380.00, 'AVAILABLE'),
  (gen_random_uuid(), '204', 2, 'DELUXE', 3, 380.00, 'AVAILABLE'),
  (gen_random_uuid(), '301', 3, 'DELUXE', 3, 380.00, 'AVAILABLE'),
  (gen_random_uuid(), '302', 3, 'DELUXE', 3, 380.00, 'OCCUPIED'),
  (gen_random_uuid(), '303', 3, 'DELUXE', 3, 380.00, 'AVAILABLE'),
  (gen_random_uuid(), '304', 3, 'SUITE', 4, 650.00, 'AVAILABLE'),
  (gen_random_uuid(), '401', 4, 'SUITE', 4, 650.00, 'AVAILABLE'),
  (gen_random_uuid(), '402', 4, 'SUITE', 4, 650.00, 'AVAILABLE'),
  (gen_random_uuid(), '403', 4, 'SUITE', 4, 650.00, 'AVAILABLE'),
  (gen_random_uuid(), '404', 4, 'SUITE', 6, 950.00, 'AVAILABLE'),
  (gen_random_uuid(), '501', 5, 'STANDARD', 2, 250.00, 'MAINTENANCE'),
  (gen_random_uuid(), '502', 5, 'STANDARD', 2, 250.00, 'AVAILABLE'),
  (gen_random_uuid(), '503', 5, 'DELUXE', 3, 380.00, 'AVAILABLE'),
  (gen_random_uuid(), '504', 5, 'SUITE', 4, 650.00, 'AVAILABLE');

-- Demo guest: João Silva, quarto 302, GOLD
INSERT INTO guests (id, name, email, phone, check_in, check_out, room_number, loyalty_tier)
VALUES (
  '11111111-1111-1111-1111-111111111111',
  'João Silva',
  'joao.silva@exemplo.com',
  '+55 11 99999-8888',
  CURRENT_DATE,
  CURRENT_DATE + INTERVAL '3 days',
  '302',
  'GOLD'
);

-- 3 spa bookings para o hóspede demo (demonstra conflito e lista)
INSERT INTO service_bookings (id, guest_id, service_type_id, scheduled_at, duration_minutes, status)
SELECT
  gen_random_uuid(),
  '11111111-1111-1111-1111-111111111111',
  st.id,
  (CURRENT_DATE + 1) + slot_time,
  60,
  'CONFIRMED'
FROM service_types st,
  (VALUES
    (TIME '10:00'),
    (TIME '14:00'),
    (TIME '16:00')
  ) AS slots(slot_time)
WHERE st.name = 'spa';
