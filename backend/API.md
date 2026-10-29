# JournalPix Backend API

Travelers' logbook & journal API. JSON under `/api`. SQLite-backed.

| Base URL | Use |
|---|---|
| `http://127.0.0.1:8080/api/...` | development (`make run`) |
| `https://walley.org/journalpix/api/...` | production (reverse-proxied) |

## Envelope

Every API response is the standard envelope:

```json
{ "success": true,  "data": { ... }, "message": null, "error": null }
```

```json
{ "success": false, "data": null, "message": null, "error": "Not Found: Trip not found" }
```

Mutations that only return a confirmation carry `message` instead of `data`
(e.g. delete endpoints).

## Error statuses

| Status | Meaning |
|---|---|
| 200 / 201 | OK / created (POST) |
| 400 | Malformed JSON / multipart, invalid field read, file >10MB |
| 401 | Missing/invalid/expired Bearer token |
| 403 | Authenticated, but the resource belongs to another user |
| 404 | Resource does not exist |
| 422 | Validation error (e.g. empty name/title, duplicate email) |
| 500 | Internal / database / file storage failure |

## Authentication

`POST /api/auth/register` and `POST /api/auth/login` are the only public API
endpoints. They return a JWT used as `Authorization: Bearer <token>` on every
other request. Tokens expire after `[auth] jwt_expiration_days` (default 30);
a failed auth is `401`. Message-style failures: `401` when the token is
missing/invalid, `403` when the token is valid but the resource belongs to
another user.

### POST /api/auth/register

```json
{ "email": "ada@example.org", "name": "Ada Lovelace", "password": "secret1" }
```

Password policy is currently just "non-empty" (no length/complexity rules).
Returns `data`: `{ "user": { id, email, name, created_at }, "token": "..." }`.

### POST /api/auth/login

```json
{ "email": "ada@example.org", "password": "secret1" }
```

Same response shape as register. Duplicate email on register → `422`.

### GET /api/auth/me (Bearer)

`data`: the current `{ id, email, name, created_at }`.

### PUT /api/auth/me (Bearer)

Partial update: `{ "name": "...", "email": "...", "password": "..." }` — any
subset of fields. Returns a confirmation message.

### DELETE /api/auth/me (Bearer)

Deletes the account (trips/places/photos cascade via the schema). Returns a
confirmation message.

## Trips

### GET /api/trips (Bearer)

All trips of the authenticated user, newest first. `data`:
`[ TripResponse, ... ]`.

`TripResponse`:

```json
{
  "id": "uuid", "user_id": "uuid",
  "title": "Italy 2026", "description": null,
  "start_date": "2026-07-01", "end_date": null,
  "status": "in_progress",
  "created_at": "RFC3339", "updated_at": "RFC3339",
  "places_count": 3, "photos_count": 12
}
```

`status` is one of `planned` · `in_progress` · `completed` · `cancelled`
(snake_case as shown).

### POST /api/trips (Bearer)

```json
{ "title": "Italy 2026", "description": "...", "start_date": "2026-07-01",
  "end_date": null, "status": "planned" }
```

Only `title` is required (201 created).

### GET /api/trips/{trip_id} (Bearer + owner)

One `TripResponse`. 404 if unknown, 403 if another user's trip.

### PUT /api/trips/{trip_id} (Bearer + owner)

Partial update with any subset of the create fields (empty `title` rejected).
Returns the updated `TripResponse`.

### DELETE /api/trips/{trip_id} (Bearer + owner)

Places and photos under the trip are deleted along with it. Confirmation
message.

## Places

A place always belongs to a trip. Ownership is derived from the trip.

### GET /api/trips/{trip_id}/places (Bearer + owner)

`data`: `[ PlaceResponse, ... ]` — `id, trip_id, name, description,
latitude, longitude, address, visit_date, order_index, created_at,
updated_at, photos_count`.

### POST /api/trips/{trip_id}/places (Bearer + owner)

```json
{ "name": "Cinque Terre",
  "description": null, "latitude": 44.1359, "longitude": 9.7974,
  "address": null, "visit_date": "2026-07-04T10:30:00", "order_index": 0 }
```

- `name` required (201 created); the `trip_id` body field is **optional and
  ignored** — the path trip wins.
- Coordinates must each be ≥ -90 when set (`422` otherwise).
- `visit_date` is timezone-naive `YYYY-MM-DDTHH:MM:SS`.

### GET /api/places/{place_id} (Bearer + owner)

`PlaceWithPhotos`: the place fields **plus** `photos: [ PhotoResponse, ... ]`.

### PUT /api/places/{place_id} (Bearer + owner)

Partial update: any subset of `{name, description, latitude, longitude,
address, visit_date, order_index}` (no `trip_id` — places don't move).

### DELETE /api/places/{place_id} (Bearer + owner)

Photos ordered under the place are deleted too (files deleted from disk).
Confirmation message.

## Photos

### GET /api/places/{place_id}/photos (Bearer + owner)

`data`: `[ PhotoResponse, ... ]` ordered by `order_index`.

`PhotoResponse`:

```json
{
  "id": "uuid", "place_id": "uuid", "user_id": "uuid",
  "file_path": "relpath.jpg", "thumbnail_path": "relpath.jpg",
  "latitude": null, "longitude": null,
  "taken_at": null, "caption": null, "order_index": 0,
  "created_at": "RFC3339", "place": null
}
```

`file_path`/`thumbnail_path` are served publicly (no token needed) at
`GET /uploads/<file_path>` and `GET /thumbnails/<thumbnail_path>`.

### POST /api/places/{place_id}/photos (Bearer + owner) — multipart

`multipart/form-data` with a field named `photo`; any accepted MIME type
(extension derived from content type). Limit: **10 MB** per file (the server
caps the whole request body at 50 MB). Returns the created `PhotoResponse`
(201). Files are stored under the configured `upload_dir`/`thumbnail_dir`;
thumbnails are currently full copies of the original image (EXIF metadata is
stubbed — see limitations).

### GET /api/photos (Bearer)

All photos of the authenticated user (newest first).

### GET /api/photos/{photo_id} (Bearer + owner)

One `PhotoResponse`.

### PUT /api/photos/{photo_id} (Bearer + owner)

Partial update: `{ caption, latitude, longitude, taken_at, order_index }`
(any subset). `place_id` in the body is accepted but has no effect.

### DELETE /api/photos/{photo_id} (Bearer + owner)

Deletes the DB row and the file/thumbnail on disk. Confirmation message.

## Timeline

### GET /api/trips/{trip_id}/timeline (Bearer + owner)

`data`:

```json
{ "trip": TripResponse,
  "timeline": [
    { "date": "2026-07-04",
      "places": [ PlaceWithPhotos, ... ] }
  ] }
```

Places grouped by `visit_date`, ordered per `order_index`.

### PUT /api/trips/{trip_id}/timeline/reorder (Bearer + owner)

Body: `[ "place_id", ... ]` — the desired order across the whole trip.
Positions are rewritten from 0 in the order given. Confirmation message.

## Audit logging (syslog local3)

Mutations log one line each in `journalpix.log`, e.g.:

```
Trip create user_id=…: success (trip_id=…)                      (info)
Place delete user_id=… place_id=…: failed (Not Found: Place not found)  (warning)
Timeline reorder user_id=… trip_id=…: success (items=2)          (info)
```

Login attempts are audited similarly (`Login attempt email=…: …`), the email
formatted with `{:?}` so it cannot forge log lines. Passwords are never
logged.

## Known limitations

- EXIF/geo metadata is stubbed (`ExifService` returns defaults).
- Thumbnails are full copies of the original image.
- No pagination on list endpoints; no complex photo query filters.
- `/uploads/*` and `/thumbnails/*` are public static files (anyone with the
  path can fetch them).
- PostgreSQL is configured but intentionally unimplemented.
