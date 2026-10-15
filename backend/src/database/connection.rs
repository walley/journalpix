// Database connection module
// Simplified version that doesn't require Diesel for SQLite

use crate::config::{Config, DatabaseType};
use std::sync::{Arc, Mutex};

#[derive(Debug, Clone)]
pub enum DatabaseConnection {
    Postgres,
    Sqlite,
}

impl DatabaseConnection {
    pub async fn new(config: &Config) -> Result<Self, Box<dyn std::error::Error + Send + Sync>> {
        match config.database_type {
            DatabaseType::Postgres => Ok(DatabaseConnection::Postgres),
            DatabaseType::Sqlite => Ok(DatabaseConnection::Sqlite),
        }
    }
}

pub type DbPool = DatabaseConnection;

pub async fn create_db_pool(config: &Config) -> Result<DbPool, Box<dyn std::error::Error + Send + Sync>> {
    DatabaseConnection::new(config).await
}
