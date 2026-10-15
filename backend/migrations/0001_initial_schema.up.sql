-- PostgreSQL migration
-- This migration creates the initial schema for JournalPix

-- Users table
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Trips table
CREATE TABLE IF NOT EXISTS trips (
    id UUID PRIMARY KEY NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    start_date DATE,
    end_date DATE,
    status VARCHAR(50) NOT NULL DEFAULT 'planned',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Places table
CREATE TABLE IF NOT EXISTS places (
    id UUID PRIMARY KEY NOT NULL,
    trip_id UUID NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    address TEXT,
    visit_date TIMESTAMP WITH TIME ZONE,
    order_index INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Photos table
CREATE TABLE IF NOT EXISTS photos (
    id UUID PRIMARY KEY NOT NULL,
    place_id UUID REFERENCES places(id) ON DELETE SET NULL,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    file_path VARCHAR(500) NOT NULL,
    thumbnail_path VARCHAR(500) NOT NULL,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    taken_at TIMESTAMP WITH TIME ZONE,
    caption TEXT,
    order_index INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_trips_user_id ON trips(user_id);
CREATE INDEX IF NOT EXISTS idx_places_trip_id ON places(trip_id);
CREATE INDEX IF NOT EXISTS idx_photos_place_id ON photos(place_id);
CREATE INDEX IF NOT EXISTS idx_photos_user_id ON photos(user_id);
CREATE INDEX IF NOT EXISTS idx_places_order ON places(order_index);
CREATE INDEX IF NOT EXISTS idx_photos_order ON photos(order_index);
