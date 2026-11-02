-- SQLite schema for JournalPix
--
-- TIME MODEL (v0.2.0)
-- -------------------
-- Every moment is stored as two columns:
--   * the instant, as TEXT in RFC3339 UTC      e.g. '2026-07-04T08:30:00+00:00'
--   * the zone,    as TEXT IANA name           e.g. 'Europe/Rome'
--
-- The zone is part of the data, not a display preference: a photo taken in
-- Tokyo keeps Tokyo wall-clock time after the traveler lands in Berlin.
-- Never compare or group on the instant alone — convert to the record's zone
-- first (a 01:00 Rome photo is 23:00 UTC the previous day).
--
-- Trip days are sequential indices (places.day, 1-based), NOT calendar dates.
-- Crossing zones can make local arrival precede local departure, so day
-- numbers are assigned by the client and only ever increment by one. A trip
-- records the zone at its start and at its end (start_timezone/end_timezone);
-- the calendar date of day N is start_date + (N-1) days.

-- Users table
CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY NOT NULL,
    email TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    password_hash TEXT NOT NULL,
    -- IANA zone used as the default when a record carries none of its own.
    timezone TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

-- Trips table
CREATE TABLE IF NOT EXISTS trips (
    id TEXT PRIMARY KEY NOT NULL,
    user_id TEXT NOT NULL,
    title TEXT NOT NULL,
    description TEXT,
    -- Day 1 of the trip, and the zone in effect at departure.
    start_date TEXT,
    start_timezone TEXT,
    -- Last day of the trip, and the zone in effect at the end.
    end_date TEXT,
    end_timezone TEXT,
    status TEXT NOT NULL DEFAULT 'planned',
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Places table
CREATE TABLE IF NOT EXISTS places (
    id TEXT PRIMARY KEY NOT NULL,
    trip_id TEXT NOT NULL,
    name TEXT NOT NULL,
    description TEXT,
    latitude REAL,
    longitude REAL,
    address TEXT,
    -- 1-based index of the trip day this place belongs to. Sequential by
    -- design: local dates can jump backwards/forwards across zones.
    day INTEGER NOT NULL DEFAULT 1,
    -- When the place was visited: instant (UTC) + zone it happened in.
    visit_date TEXT,
    visit_timezone TEXT,
    order_index INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE
);

-- Photos table
CREATE TABLE IF NOT EXISTS photos (
    id TEXT PRIMARY KEY NOT NULL,
    place_id TEXT,
    user_id TEXT NOT NULL,
    file_path TEXT NOT NULL,
    thumbnail_path TEXT NOT NULL,
    latitude REAL,
    longitude REAL,
    -- When the shutter fired: instant (UTC) + zone it happened in.
    taken_at TEXT,
    taken_timezone TEXT,
    caption TEXT,
    order_index INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY (place_id) REFERENCES places(id) ON DELETE SET NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_trips_user_id ON trips(user_id);
CREATE INDEX IF NOT EXISTS idx_places_trip_id ON places(trip_id);
CREATE INDEX IF NOT EXISTS idx_places_day ON places(day);
CREATE INDEX IF NOT EXISTS idx_photos_place_id ON photos(place_id);
CREATE INDEX IF NOT EXISTS idx_photos_user_id ON photos(user_id);
CREATE INDEX IF NOT EXISTS idx_photos_order ON photos(order_index);

-- Create triggers for automatic timestamp updates.
-- NOTE: these write offset-less UTC (datetime('now')); the readers in
-- database/connection.rs accept both that and full RFC3339.
CREATE TRIGGER IF NOT EXISTS update_users_timestamp
AFTER UPDATE ON users
FOR EACH ROW
BEGIN
    UPDATE users SET updated_at = datetime('now') WHERE id = OLD.id;
END;

CREATE TRIGGER IF NOT EXISTS update_trips_timestamp
AFTER UPDATE ON trips
FOR EACH ROW
BEGIN
    UPDATE trips SET updated_at = datetime('now') WHERE id = OLD.id;
END;

CREATE TRIGGER IF NOT EXISTS update_places_timestamp
AFTER UPDATE ON places
FOR EACH ROW
BEGIN
    UPDATE places SET updated_at = datetime('now') WHERE id = OLD.id;
END;
