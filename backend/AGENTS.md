# JournalPix Backend — Agent Notes

Travelers' logbook & journal backend. Rust / actix-web 4, JWT auth, photo
uploads with EXIF (stub), **SQLite** as the primary database.

## Running

```bash
make run                        # dev: cargo run (port 8080)
cargo build
./target/debug/journalpix-backend          # built-in defaults
./target/debug/journalpix-backend --config my.conf
```

The HTTP listener is hard-coded to `127.0.0.1` (loopback) in `src/main.rs`.
There is no host/bind address setting: the backend cannot be exposed on other
interfaces. Use a reverse proxy on the same host if external access is needed.

Production deployment: **walley.org** is Apache and `/journalpix` is
reverse-proxied to the loopback listener. Public URLs therefore carry the
`/journalpix` prefix, e.g. `https://walley.org/journalpix/health` — the root
about page links to that public health URL rather than the internal `/health`.

Logging goes exclusively to syslog facility **local3**: request/access logs at
info (`local3.info`), errors and 5xx requests at err (`local3.err`). There is
deliberately no stderr/file fallback: if no local syslog socket (`/dev/log`,
`/var/run/syslog` or `/var/run/log`) is reachable, the process reports the
error on stderr and exits with status 1 instead of starting.

### Configuration

INI style (see `journalpix.conf.example`), priority:
**built-in defaults < config file < environment variables** (env kept for
backward compat). Config lookup order: `--config <path>` arg, `$CONFIG_PATH`,
`./journalpix.conf`, `/usr/local/etc/journalpix.conf`, `/etc/journalpix.conf`.
All keys optional; with no file at all the server runs on port 8080 with a
SQLite db in the cwd. Sections: `[server] port`, `[database] type|url`,
`[auth] jwt_secret|jwt_expiration_days`, `[storage] upload_dir|thumbnail_dir`,
`[cors] origins`. Parser has unit tests in `src/config.rs`.

**`[auth] jwt_secret`** is the signing key for login tokens (JWTs): it lets
the server verify `Authorization: Bearer <token>` without storing sessions.
Anyone who knows the secret can forge a token for ANY user, so there is NO
built-in default: when unset, main.rs generates a **random ephemeral secret
at every startup** (safe, but logins don't survive restarts — users just
log in again). Set a fixed value for persistent sessions (generate with
`head -c 32 /dev/urandom | base64`). Rotating the secret never loses
accounts or data (passwords are bcrypt-hashed; tokens just become invalid).
Full explanation lives in `journalpix.conf.example`.

### Install (systemd-style layout)

```bash
make install                    # PREFIX=/usr/local default, run as root
make install PREFIX=/opt/jpx    # custom prefix
make uninstall                  # keeps a modified config
```

Installs `PREFIX/bin/journalpix-backend` (release build) and
`PREFIX/etc/journalpix.conf` (from the example, never clobbering an existing
one) + `.conf.example`. The installed binary is self-contained (SQLite schema
is embedded via `include_str!`) and must run as a **non-root user** from a
writable working directory (db/uploads/thumbnails default to relative paths
there).<arg_key>old_string</arg_key><arg_value>## Running

```bash
cargo build
DATABASE_TYPE=sqlite ./target/debug/journalpix-backend   # port 8080
# or: cargo run  (reads .env via dotenv)
```

DB defaults to `sqlite:journalpix.db`. Uploads in `./uploads`,
thumbnails in `./thumbnails` (both auto-created; served at
`GET /uploads/<file>` and `GET /thumbnails/<file>`).

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

DB defaults to `sqlite:journalpix.db` (relative to the process working
directory). Uploads in `./uploads`, thumbnails in `./thumbnails` (both
auto-created; served at `GET /uploads/<file>` and `GET /thumbnails/<file>`).

## API (all under `/api`, JSON)

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | / | – | HTML about page (outside /api) |
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

## Frontend scripts

Both use only `curl` + core `perl` (`JSON::PP`) — no python, no jq required.

`scripts/jpx.sh` — full frontend for the API:

```bash
scripts/jpx.sh                  # interactive menu (all 22 operations)
scripts/jpx.sh register <email> <name> <password>
scripts/jpx.sh login <email> <password>
scripts/jpx.sh trips | new-trip | trip <id> | update-trip <id> | del-trip <id>
scripts/jpx.sh places <trip_id> | new-place <trip_id> | place <id> | ...
scripts/jpx.sh upload <place_id> <file>          # multipart photo upload
scripts/jpx.sh download <photo_id> [dest_dir]    # fetch image to disk
scripts/jpx.sh photos [place_id] | photo <id> | timeline <trip_id>
scripts/jpx.sh raw /trips                        # dump raw JSON of any endpoint
BASE_URL=http://host:8080 scripts/jpx.sh ...
```

Token is cached in `/tmp/jpx_token` after login/register. Output is
pretty-printed (tables for trips/places/photos, simplified timeline).

`scripts/test_api.sh` — automated smoke test (29 checks, full CRUD +
upload/download + auth failure paths):

```bash
scripts/test_api.sh                  # runs all endpoints against localhost:8080
scripts/test_api.sh health          # run one section
scripts/test_api.sh auth|trips|places|photos|timeline|crud
BASE_URL=http://host:8080 scripts/test_api.sh
```

