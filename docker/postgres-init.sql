-- Creates the ai_data database (hotel_info is created via POSTGRES_DB env var)
CREATE DATABASE ai_data;

GRANT ALL PRIVILEGES ON DATABASE hotel_info TO hotel;
GRANT ALL PRIVILEGES ON DATABASE ai_data TO hotel;
