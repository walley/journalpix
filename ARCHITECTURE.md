# Travel Vlog Application Architecture

## Overview

A travel vlogging platform where users upload photos, the system extracts geolocation data, and creates an editable timeline/journal of their travels. Users can also plan trips in advance.

## System Components

### 1. Backend Services (Rust)
- **REST API**: Actix-web based
- **Photo Processing**: EXIF metadata extraction for geolocation
- **Database**: PostgreSQL with Diesel ORM
- **Authentication**: JWT-based auth
- **File Storage**: Local or S3-compatible

### 2. Frontend (Android)
- **Language**: Kotlin
- **Architecture**: MVVM with Jetpack Compose
- **Networking**: Retrofit for API calls
- **Local Storage**: Room database for offline support

### 3. Data Flow

```
Android App -> REST API -> Rust Backend -> PostgreSQL
                      -> Photo Storage
```

## API Endpoints

### Authentication
- `POST /api/auth/register` - User registration
- `POST /api/auth/login` - User login
- `GET /api/auth/me` - Current user info

### Trips
- `GET /api/trips` - List all user trips
- `POST /api/trips` - Create new trip
- `GET /api/trips/{id}` - Get trip details
- `PUT /api/trips/{id}` - Update trip
- `DELETE /api/trips/{id}` - Delete trip

### Places
- `GET /api/trips/{trip_id}/places` - List places in trip
- `POST /api/trips/{trip_id}/places` - Add place to trip
- `GET /api/places/{id}` - Get place details
- `PUT /api/places/{id}` - Update place
- `DELETE /api/places/{id}` - Delete place

### Photos
- `POST /api/places/{place_id}/photos` - Upload photo
- `GET /api/places/{place_id}/photos` - List photos for place
- `GET /api/photos/{id}` - Get photo details
- `DELETE /api/photos/{id}` - Delete photo

### Timeline
- `GET /api/trips/{trip_id}/timeline` - Get trip timeline with places and photos

## Database Schema

### Users
- id (UUID, PK)
- email (String, unique)
- password_hash (String)
- name (String)
- created_at (Timestamp)
- updated_at (Timestamp)

### Trips
- id (UUID, PK)
- user_id (UUID, FK to Users)
- title (String)
- description (Text)
- start_date (Date)
- end_date (Date)
- status (String: planned, in_progress, completed)
- created_at (Timestamp)
- updated_at (Timestamp)

### Places
- id (UUID, PK)
- trip_id (UUID, FK to Trips)
- name (String)
- description (Text)
- latitude (Float)
- longitude (Float)
- address (String)
- visit_date (DateTime)
- order_index (Integer)
- created_at (Timestamp)
- updated_at (Timestamp)

### Photos
- id (UUID, PK)
- place_id (UUID, FK to Places, nullable)
- user_id (UUID, FK to Users)
- file_path (String)
- thumbnail_path (String)
- latitude (Float)
- longitude (Float)
- taken_at (DateTime)
- caption (Text)
- order_index (Integer)
- created_at (Timestamp)

## Technology Stack

### Backend
- Rust 1.70+
- Actix-web 4.0
- Diesel 2.0 (PostgreSQL)
- Serde for serialization
- jsonwebtoken for auth
- exif for photo metadata extraction
- tokio for async operations

### Android
- Kotlin 1.9+
- Android Studio / Gradle
- Jetpack Compose for UI
- Retrofit 2 for networking
- Room for local database
- Coil for image loading
- Location services for GPS

### DevOps
- Docker for containerization
- Docker Compose for local development
- GitHub Actions for CI/CD
