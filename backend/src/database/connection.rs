// Database connection module.
// SQLite is the primary, fully-supported backend (rusqlite, bundled).
// PostgreSQL remains a config option but is not implemented yet.

use crate::config::{Config, DatabaseType};
use rusqlite::Connection;
use std::sync::{Arc, Mutex};

#[derive(Clone)]
pub struct DatabaseConnection {
    conn: Arc<Mutex<Connection>>,
}

impl std::fmt::Debug for DatabaseConnection {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("DatabaseConnection").finish()
    }
}

pub type DbPool = DatabaseConnection;

pub const PHOTO_COLS: &str =
    "id, place_id, user_id, file_path, thumbnail_path, latitude, longitude, taken_at, caption, order_index, created_at";

impl DatabaseConnection {
    /// Open the database described by `config` and run migrations.
    pub fn new(config: &Config) -> Result<Self, Box<dyn std::error::Error + Send + Sync>> {
        match config.database_type {
            DatabaseType::Sqlite => Self::open_sqlite(&config.database_url),
            DatabaseType::Postgres => Err(
                "PostgreSQL is configured but not implemented yet. Set DATABASE_TYPE=sqlite."
                    .into(),
            ),
        }
    }

    fn open_sqlite(url: &str) -> Result<Self, Box<dyn std::error::Error + Send + Sync>> {
        // Accept "sqlite:path", "sqlite://path" or a bare path.
        let path = url
            .strip_prefix("sqlite://")
            .or_else(|| url.strip_prefix("sqlite:"))
            .unwrap_or(url);

        let conn = if path.is_empty() || path == ":memory:" {
            Connection::open_in_memory()?
        } else {
            Connection::open(path)?
        };

        conn.execute_batch(
            "PRAGMA journal_mode = WAL;
             PRAGMA foreign_keys = ON;",
        )?;

        let db = Self {
            conn: Arc::new(Mutex::new(conn)),
        };
        db.run_migrations()?;
        Ok(db)
    }

    fn run_migrations(&self) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        // The schema is embedded in the binary so the installed executable
        // is self-contained; the file is preferred in dev if it exists.
        const EMBEDDED_SCHEMA: &str =
            include_str!("../../migrations/0002_sqlite_schema.sql");
        let schema = match std::fs::read_to_string("migrations/0002_sqlite_schema.sql") {
            Ok(s) => s,
            Err(_) => EMBEDDED_SCHEMA.to_string(),
        };
        let conn = self.conn.lock().unwrap();
        conn.execute_batch(&schema)?;
        Ok(())
    }

    /// Run a closure with the underlying connection. Blocking; the mutex
    /// serializes access, which is fine for SQLite.
    pub fn with_conn<T>(
        &self,
        f: impl FnOnce(&Connection) -> Result<T, rusqlite::Error>,
    ) -> Result<T, rusqlite::Error> {
        let conn = self.conn.lock().unwrap();
        f(&conn)
    }
}

// Keep async signature used by main.rs.
pub async fn create_db_pool(config: &Config) -> Result<DbPool, Box<dyn std::error::Error + Send + Sync>> {
    DatabaseConnection::new(config)
}

// ---------------------------------------------------------------------------
// Small row-mapping helpers shared by the services.

use rusqlite::{types::ValueRef, Row};

pub fn get_uuid(row: &Row, idx: usize) -> Result<uuid::Uuid, rusqlite::Error> {
    let s: String = row.get(idx)?;
    uuid::Uuid::parse_str(&s).map_err(|e| rusqlite::Error::ToSqlConversionFailure(e.into()))
}

pub fn get_opt_uuid(row: &Row, idx: usize) -> Result<Option<uuid::Uuid>, rusqlite::Error> {
    match row.get_ref(idx)? {
        ValueRef::Null => Ok(None),
        ValueRef::Text(s) => Ok(Some(
            uuid::Uuid::parse_str(&String::from_utf8_lossy(s))
                .map_err(|e| rusqlite::Error::ToSqlConversionFailure(e.into()))?,
        )),
        _ => Ok(None),
    }
}

pub fn get_opt_string(row: &Row, idx: usize) -> Result<Option<String>, rusqlite::Error> {
    row.get(idx)
}

pub fn get_opt_f64(row: &Row, idx: usize) -> Result<Option<f64>, rusqlite::Error> {
    row.get(idx)
}

pub fn get_opt_i64(row: &Row, idx: usize) -> Result<Option<i64>, rusqlite::Error> {
    row.get(idx)
}

pub fn get_timestamp(row: &Row, idx: usize) -> Result<chrono::DateTime<chrono::Utc>, rusqlite::Error> {
    let s: String = row.get(idx)?;
    chrono::DateTime::parse_from_rfc3339(&s)
        .or_else(|_| {
            chrono::NaiveDateTime::parse_from_str(&s, "%Y-%m-%d %H:%M:%S")
                .map(|n| n.and_utc().fixed_offset())
        })
        .map(|dt| dt.with_timezone(&chrono::Utc))
        .map_err(|e| rusqlite::Error::FromSqlConversionFailure(idx, rusqlite::types::Type::Text, e.into()))
}

pub fn get_opt_naive_datetime(
    row: &Row,
    idx: usize,
) -> Result<Option<chrono::NaiveDateTime>, rusqlite::Error> {
    match row.get_ref(idx)? {
        ValueRef::Null => Ok(None),
        ValueRef::Text(s) => {
            let s = String::from_utf8_lossy(s);
            chrono::NaiveDateTime::parse_from_str(&s, "%Y-%m-%dT%H:%M:%S")
                .or_else(|_| chrono::NaiveDateTime::parse_from_str(&s, "%Y-%m-%d %H:%M:%S"))
                .map(Some)
                .map_err(|e| {
                    rusqlite::Error::FromSqlConversionFailure(
                        idx,
                        rusqlite::types::Type::Text,
                        e.into(),
                    )
                })
        }
        _ => Ok(None),
    }
}

pub fn get_opt_naive_date(row: &Row, idx: usize) -> Result<Option<chrono::NaiveDate>, rusqlite::Error> {
    match row.get_ref(idx)? {
        ValueRef::Null => Ok(None),
        ValueRef::Text(s) => {
            let s = String::from_utf8_lossy(s);
            let s = s.split('T').next().unwrap_or(&s);
            chrono::NaiveDate::parse_from_str(s, "%Y-%m-%d")
                .map(Some)
                .map_err(|e| {
                    rusqlite::Error::FromSqlConversionFailure(
                        idx,
                        rusqlite::types::Type::Text,
                        e.into(),
                    )
                })
        }
        _ => Ok(None),
    }
}

pub fn get_i64_count(row: &Row, idx: usize) -> Result<i64, rusqlite::Error> {
    row.get(idx)
}

pub fn get_trip_status(row: &Row, idx: usize) -> Result<crate::models::trip::TripStatus, rusqlite::Error> {
    let s: String = row.get(idx)?;
    Ok(match s.as_str() {
        "in_progress" => crate::models::trip::TripStatus::InProgress,
        "completed" => crate::models::trip::TripStatus::Completed,
        "cancelled" => crate::models::trip::TripStatus::Cancelled,
        _ => crate::models::trip::TripStatus::Planned,
    })
}
