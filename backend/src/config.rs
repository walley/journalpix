use std::env;

#[derive(Debug, Clone)]
pub struct Config {
    pub database_url: String,
    pub database_type: DatabaseType,
    pub jwt_secret: String,
    pub jwt_expiration_days: i64,
    pub upload_dir: String,
    pub thumbnail_dir: String,
    pub server_port: u16,
    pub cors_origins: Vec<String>,
}

#[derive(Debug, Clone, PartialEq)]
pub enum DatabaseType {
    Postgres,
    Sqlite,
}

impl DatabaseType {
    pub fn from_str(s: &str) -> Self {
        match s.to_lowercase().as_str() {
            "sqlite" => DatabaseType::Sqlite,
            _ => DatabaseType::Postgres,
        }
    }
}

impl Config {
    pub fn from_env() -> Self {
        let database_url = env::var("DATABASE_URL")
            .unwrap_or_else(|_| "postgres://localhost:5432/journalpix".to_string());
        
        let database_type = env::var("DATABASE_TYPE")
            .map(|s| DatabaseType::from_str(&s))
            .unwrap_or(DatabaseType::Postgres);
        
        // If using SQLite, default to a local file
        let database_url = if database_type == DatabaseType::Sqlite && 
            !database_url.starts_with("sqlite:") && 
            !database_url.ends_with(".db") && 
            !database_url.ends_with(".sqlite") {
            "sqlite:journalpix.db".to_string()
        } else {
            database_url
        };
        
        Config {
            database_url,
            database_type,
            jwt_secret: env::var("JWT_SECRET")
                .unwrap_or_else(|_| "default_secret_change_in_production".to_string()),
            jwt_expiration_days: env::var("JWT_EXPIRATION_DAYS")
                .map(|s| s.parse().unwrap_or(30))
                .unwrap_or(30),
            upload_dir: env::var("UPLOAD_DIR")
                .unwrap_or_else(|_| "./uploads".to_string()),
            thumbnail_dir: env::var("THUMBNAIL_DIR")
                .unwrap_or_else(|_| "./thumbnails".to_string()),
            server_port: env::var("SERVER_PORT")
                .map(|s| s.parse().unwrap_or(8080))
                .unwrap_or(8080),
            cors_origins: env::var("CORS_ORIGINS")
                .map(|s| s.split(',').map(|s| s.trim().to_string()).collect())
                .unwrap_or(vec!["http://localhost:3000".to_string()]),
        }
    }
}

impl Default for Config {
    fn default() -> Self {
        Self::from_env()
    }
}
