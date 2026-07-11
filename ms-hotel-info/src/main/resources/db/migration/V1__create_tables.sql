CREATE TABLE guests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    phone VARCHAR(50),
    check_in DATE,
    check_out DATE,
    room_number VARCHAR(20),
    loyalty_tier VARCHAR(20) NOT NULL DEFAULT 'STANDARD'
);

CREATE TABLE rooms (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    number VARCHAR(20) NOT NULL UNIQUE,
    floor INTEGER,
    type VARCHAR(20) NOT NULL,
    capacity INTEGER,
    price_per_night DECIMAL(10,2),
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE'
);

CREATE TABLE service_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    open_time TIME,
    close_time TIME,
    slot_duration_minutes INTEGER,
    price_per_slot DECIMAL(10,2)
);

CREATE TABLE service_bookings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    guest_id UUID NOT NULL REFERENCES guests(id),
    service_type_id UUID NOT NULL REFERENCES service_types(id),
    scheduled_at TIMESTAMP NOT NULL,
    duration_minutes INTEGER,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    notes TEXT
);

CREATE TABLE room_bookings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    guest_id UUID NOT NULL REFERENCES guests(id),
    room_id UUID NOT NULL REFERENCES rooms(id),
    check_in DATE NOT NULL,
    check_out DATE NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    total_price DECIMAL(10,2)
);
