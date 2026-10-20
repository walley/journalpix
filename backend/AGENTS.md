# JournalPix Backend — Agent Notes

Travelers' logbook & journal backend. Rust / actix-web 4, JWT auth, photo
uploads with EXIF (stub), **SQLite** as the primary database.

## Repo layout

- `/home/walley/git/journalpix/backend/` — this backend (the only part covered here).
  `../` (repo root) also contains the frontend; irrelevant for backend work.
- `src/main.rs` — app bootstrap, app_data registration, static file serving.
- `src/config.rs` — env config (`Config::from_env`), `DATABASE_TYPE` switch.
- `src/database/connection.rs` — SQLite (`rusqlite`, bundled) behind
  `Arc<Mutex<Connection>>`; `with_conn()` runs migrations from
  `migrations/0002_sqlite_schema.sql` on startup and serializes access.
  Also contains shared row-mapping helpers (`get_uuid`, `get_timestamp`,
  `get_opt_naive_datetime`, `PHOTO_COLS`, ...).
- `src/routes/` — HTTP handlers (`auth`, `trips`, `places`, `photos`,
  `timeline`, `health`). Each module exposes `configure(cfg)`.
- `src/services/` — business logic + all SQL (`user`, `trip`, `place`,
  `photo`, `timeline`). Handlers construct services per-request from
  `web::Data<Arc<DatabaseConnection>>`.
- `src/models/` — request/response DTOs (serde). `ApiResponse<T>` envelope:
  `{"success":bool,"data":..,"message":..,"error":..}`.
- `src/utils/` — JWT/bcrypt (`auth`), `ApiError` (`error`), validation,
  `FileService` (uploads/thumbnails), `ExifService` (stub — returns
  defaults; real EXIF parsing not implemented).
- `migrations/0001_*.sql` — PostgreSQL schema (kept, but **postgres is NOT
  implemented**; `DATABASE_TYPE=postgres` fails at startup by design).
- `migrations/0002_sqlite_schema.sql` — the live schema, executed on every
  startup (idempotent, `IF NOT EXISTS`).

## Running

```bash
cargo build
DATABASE_TYPE=sqlite ./target/debug/journalpix-backend   # port 8080
# or: cargo run  (reads .env via dotenv)
```

DB defaults to `sqlite:journalpix.db`. Uploads in `./uploads`,
thumbnails in `./thumbnails` (both auto-created; served at
`GET /uploads/<file>` and `GET /thumbnails/<file>`).

Env vars: see `.env.example` (`DATABASE_URL`, `DATABASE_TYPE`,
`JWT_SECRET`, `JWT_EXPIRATION_DAYS`, `UPLOAD_DIR`, `THUMBNAIL_DIR`,
`SERVER_PORT`, `CORS_ORIGINS`).

## API (all under `/api`, JSON)

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | /health | – | outside /api |
| POST | /api/auth/register | – | `{email,name,password}` (pwd ≥ 8 chars) → token |
| POST | /api/auth/login | – | `{email,password}` → token |
| GET | /api/auth/me | Bearer | current user |
| PUT | /api/auth/me | Bearer | update name/email/password |
| DELETE | /api/auth/me | Bearer | delete account |
| GET/POST | /api/trips | Bearer | list (of auth user) / create `{title,description?,start_date?,end_date?,status?}` |
| GET/PUT/DELETE | /api/trips/{trip_id} | – | trip detail/update/delete |
| GET/POST | /api/trips/{trip_id}/places | – | places of a trip / create (body `trip_id` ignored, path wins) |
| GET/PUT/DELETE | /api/places/{place_id} | – | detail includes `photos` |
| GET | /api/places/{place_id}/photos | – | list photos of place |
| POST | /api/places/{place_id}/photos | Bearer | **multipart** upload (`photo` field, ≤10MB) |
| GET | /api/photos | Bearer | all photos of auth user |
| GET/PUT/DELETE | /api/photos/{photo_id} | – | |
| GET | /api/trips/{trip_id}/timeline | – | places grouped by visit date |
| PUT | /api/trips/{trip_id}/timeline/reorder | – | body: `[place_id, ...]` new order |

`TripStatus` serializes snake_case: `planned`, `in_progress`, `completed`,
`cancelled`.

## Critical gotchas (bugs already fixed — don't reintroduce)

1. **actix-web scope prefixes are literal** — `{param}` in a scope prefix
   (e.g. `web::scope("/trips/{trip_id}/places")`) never matches. Nested
   resource routes must be registered as plain full-path routes:
   `cfg.route("/trips/{trip_id}/places", ...)`.
2. **Route registration order matters**: a broad scope (`/trips`) that
   matches a prefix but has no matching inner route 404s without falling
   through to later scopes. Specific scopes/routes are registered first in
   `main.rs` (auth, places, photos, timeline, then trips).
3. **`web::Data` must be registered before use**: `AuthService` and
   `Arc<FileService>` are `app_data` in `main.rs`. Missing registration
   panics at request time (500s). `DatabaseConnection` is registered as
   `Data<Arc<DatabaseConnection>>`.
4. **rusqlite without chrono feature**: never `row.get::<NaiveDateTime>` —
   use the helpers in `database/connection.rs` (`get_opt_naive_datetime`,
   `get_timestamp`, ...). Dates are stored as TEXT
   (`YYYY-MM-DD`, `YYYY-MM-DDTHH:MM:SS`, or RFC3339 for timestamps).
5. Auth is **not** middleware — protected handlers call
   `routes::auth_user_id(&req, &auth_service)` which parses the
   `Authorization: Bearer <jwt>` header. Ownership checks (does this trip
   belong to the caller?) are mostly NOT enforced on `{id}` routes — a
   known limitation, fine for MVP.
6. SQLite access is via `db_pool.with_conn(|conn| ...)` (synchronous,
   mutex-guarded). Do all bcrypt hashing *outside* the closure — it returns
   `rusqlite::Error`, not `ApiError`.
7. `verify_password`/`hash_password` return `Result<_, ApiError>` directly.

## Known limitations / TODO

- PostgreSQL path intentionally unimplemented (schema kept in migrations/0001).
- `ExifService::extract_metadata` returns empty metadata — wire a real EXIF
  parser (e.g. `kamadak-exif`) if geolocation from photos is needed.
- Thumbnails are just copies of the original; use the `image` crate (already
  a dependency) to generate real scaled thumbnails.
- `middleware/auth.rs` (`actix-web-httpauth` bearer middleware) exists but
  is unused; handlers do manual header parsing instead.
- `timeline reorder` ignores auth; places/photos routes don't verify the
  resource owner.

## Test frontend

`scripts/test_api.sh` — curl-based smoke test of the full API:

```bash
scripts/test_api.sh                  # runs all endpoints against localhost:8080
scripts/test_api.sh health          # run one section
scripts/test_api.sh auth|trips|places|photos|timeline|crud
BASE_URL=http://host:8080 scripts/test_api.sh
```

Requires `curl`, `python3` (JSON parsing) and `file`/`printf` for a dummy PNG.

## Terminal / TUI navigation

When driving interactive full-screen TUI apps (midnight commander, vim,
htop, etc.) through the terminal tool, always send arrow keys using the
**application cursor key sequences**, not the ANSI sequences.

- Up:    `ESC O A`
- Down:  `ESC O B`
- Right: `ESC O C`
- Left:  `ESC O D`

Use `ESC [ A` / `ESC [ B` / `ESC [ C` / `ESC [ D` only if the application
is known NOT to use application cursor key mode.

TUI programs enable application cursor key mode (`\x1b[?1h`), which causes
the terminal to emit `ESC O <letter>` for arrow keys. Sending the wrong
sequence (`ESC [ <letter>`) is silently ignored, so the cursor appears not
to move.

To type these via the terminal tool, send the literal bytes, e.g. Down is
`\u001bOB`, Enter is `\r`, Tab is `\t`, Escape is `\u001b`.
