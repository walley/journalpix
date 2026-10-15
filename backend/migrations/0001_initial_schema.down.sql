-- PostgreSQL migration rollback
-- This migration drops all tables created in the initial schema

DROP TABLE IF EXISTS photos;
DROP TABLE IF EXISTS places;
DROP TABLE IF EXISTS trips;
DROP TABLE IF EXISTS users;
